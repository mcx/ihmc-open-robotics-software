package us.ihmc.perception.opencl;

import org.apache.commons.lang3.StringUtils;
import org.bytedeco.javacpp.*;
import org.bytedeco.opencl.*;
import org.bytedeco.opencl.global.OpenCL;
import us.ihmc.log.LogTools;
import us.ihmc.tools.string.StringTools;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.TreeSet;

import static org.bytedeco.opencl.global.OpenCL.*;

/**
 * Reference: https://www.khronos.org/registry/OpenCL/sdk/2.2/docs/man/html/
 * Use `clinfo` to get more info about your setup.
 */
public class OpenCLManager
{
   static
   {
      Loader.load(OpenCL.class);
   }

   private static _cl_platform_id platforms = new _cl_platform_id();
   private static _cl_device_id devices = new _cl_device_id();
   private static _cl_context context = null;
   private static _cl_command_queue commandQueue = null;
   private static final IntPointer numberOfDevices = new IntPointer(1);
   private static final IntPointer numberOfPlatforms = new IntPointer(3);
   private static volatile boolean initialized = false;

   private final ArrayList<_cl_program> programs = new ArrayList<>();
   private final ArrayList<_cl_kernel> kernels = new ArrayList<>();
   private final TreeSet<_cl_mem> bufferObjects = new TreeSet<>(Comparator.comparing(Pointer::address));
   private final SizeTPointer globalWorkSize = new SizeTPointer(0, 0, 0);
   private final PointerPointer tempPointerPointerForSetKernelArgument = new PointerPointer(1);
   private final long pointerPointerSize = Pointer.sizeof(PointerPointer.class);
   private final long intPointerSize = Pointer.sizeof(IntPointer.class);
   private final SizeTPointer origin = new SizeTPointer(3);
   private final SizeTPointer region = new SizeTPointer(3);
   private final IntPointer returnCode = new IntPointer(1);
   //   private final SizeTPointer localWorkSize = new SizeTPointer(1024, 0, 0); // TODO: Rethink this

   public OpenCLManager()
   {
      if (!initialized)
      {
         /* Get platform/device information */
         final int platformCount = 1; // We're just interested in the primary platform (most likely "NVIDIA CUDA")
         checkReturnCode(clGetPlatformIDs(platformCount, platforms, numberOfPlatforms));
         checkReturnCode(clGetDeviceIDs(platforms, CL_DEVICE_TYPE_ALL, platformCount, devices, numberOfDevices));

         int numberOfPlatforms = OpenCLManager.numberOfPlatforms.get();
         LogTools.info("Number of platforms: {}", numberOfPlatforms);
         int numberOfDevices = OpenCLManager.numberOfDevices.get();
         LogTools.info("Number of devices: {}", numberOfDevices);

         for (int i = 0; i < numberOfPlatforms; i++)
         {
            String message = "OpenCL Platform:";
            message += " Name: " + readPlatformInfoParameter(i, CL_PLATFORM_NAME);
            message += " Vendor: " + readPlatformInfoParameter(i, CL_PLATFORM_VENDOR);
            message += " Version: " + readPlatformInfoParameter(i, CL_PLATFORM_VERSION);
            LogTools.info(message);
         }

         for (int i = 0; i < numberOfDevices; i++)
         {
            String message = "OpenCL Device:";
            message += " Name: " + readDeviceInfoParameter(i, CL_DEVICE_NAME);
            message += " Vendor: " + readDeviceInfoParameter(i, CL_DEVICE_VENDOR);
            message += " Driver Version: " + readDeviceInfoParameter(i, CL_DRIVER_VERSION);
            LogTools.info(message);
         }

         /* Create OpenCL Context */
         context = clCreateContext(null, 1, devices, null, null, returnCode);
         checkReturnCode();

         /* Create Command Queue */
         LongPointer properties = null;
         commandQueue = clCreateCommandQueueWithProperties(context, devices, properties, returnCode);
         checkReturnCode();

         initialized = true;

         Thread shutdownHook = new Thread(() ->
         {
            checkReturnCode(clFlush(commandQueue));
            checkReturnCode(clFinish(commandQueue));
            checkReturnCode(clReleaseCommandQueue(commandQueue));
            checkReturnCode(clReleaseContext(context));
         }, "OpenCLManager-Shutdown-Hook");
         Runtime.getRuntime().addShutdownHook(shutdownHook);
      }
   }

   private String readPlatformInfoParameter(int i, int parameterName)
   {
      return OpenCLTools.readString((stringSizeByteLimit, stringPointer, resultingStringLengthPointer) ->
      {
         checkReturnCode(clGetPlatformInfo(platforms.position(i).getPointer(),
                                           parameterName,
                                           stringSizeByteLimit,
                                           stringPointer,
                                           resultingStringLengthPointer));
      });
   }

   private String readDeviceInfoParameter(int i, int parameterName)
   {
      return OpenCLTools.readString((stringSizeByteLimit, stringPointer, resultingStringLengthPointer) ->
      {
         checkReturnCode(clGetDeviceInfo(devices.position(i).getPointer(),
                                         parameterName,
                                         stringSizeByteLimit,
                                         stringPointer,
                                         resultingStringLengthPointer));
      });
   }

   public _cl_kernel loadSingleFunctionProgramAndCreateKernel(String programName, String... headerFilesToInclude)
   {
      _cl_program program = loadProgram(programName, headerFilesToInclude);
      return createKernel(program, StringUtils.uncapitalize(programName));
   }

   public _cl_program loadProgram(String programName, String... headerFilesToInclude)
   {
      String sourceAsString = "";

      ArrayList<String> includedHeaders = new ArrayList<>();
      includedHeaders.add("EuclidCommon.cl");
      includedHeaders.addAll(Arrays.asList(headerFilesToInclude));

      for (String includedHeader : includedHeaders)
      {
         Path headerFilePath = Paths.get("openCL", includedHeader);
         LogTools.info("Loading OpenCL program: openCL/{}", includedHeader);
         sourceAsString += OpenCLTools.readFile(headerFilePath) + "\n";
      }

      Path programPath = Paths.get("openCL", programName + ".cl");
      LogTools.info("Loading OpenCL program: {}", programPath);
      sourceAsString += OpenCLTools.readFile(programPath);

      // Support loading from CRLF (Windows) checkouts
      sourceAsString = StringTools.filterOutCRLFLineEndings(sourceAsString);

      /* Create Kernel program from the read in source */
      int count = 1;
      _cl_program program = clCreateProgramWithSource(context,
                                                      count,
                                                      new PointerPointer(sourceAsString),
                                                      new SizeTPointer(1).put(sourceAsString.length()),
                                                      returnCode);
      checkReturnCode();
      programs.add(program);

      /* Build Kernel Program */
      int numberOfDevices = 1;
      String options = null;
      Pfn_notify__cl_program_Pointer notificationRoutine = null;
      Pointer userData = null;
      int returnCode = clBuildProgram(program, numberOfDevices, devices, options, notificationRoutine, userData);
      LogTools.info("OpenCL build info for openCL/{}.cl: \n{}",
                    programName,
                    OpenCLTools.readString((stringSizeByteLimit, stringPointer, resultingStringLengthPointer) ->
                                           {
                                              clGetProgramBuildInfo(program, devices.getPointer(), CL_PROGRAM_BUILD_LOG, stringSizeByteLimit, stringPointer, resultingStringLengthPointer);
                                           }));
      checkReturnCode(returnCode);

      return program;
   }

   public _cl_kernel createKernel(_cl_program program, String kernelName)
   {
      /* Create OpenCL Kernel */
      _cl_kernel kernel = clCreateKernel(program, kernelName, returnCode);
      checkReturnCode();
      kernels.add(kernel);
      return kernel;
   }

   public _cl_mem createBufferObject(long sizeInBytes)
   {
      Pointer hostPointer = null;
      return createBufferObject(sizeInBytes, hostPointer);
   }

   public _cl_mem createBufferObject(long sizeInBytes, Pointer hostPointer)
   {
      int flags = CL_MEM_READ_WRITE;
      return createBufferObject(flags, sizeInBytes, hostPointer);
   }

   public _cl_mem createBufferObject(int flags, long sizeInBytes, Pointer hostPointer)
   {
      if (hostPointer != null)
         flags |= CL_MEM_USE_HOST_PTR;
      _cl_mem bufferObject = clCreateBuffer(context, flags, sizeInBytes, hostPointer, returnCode);
      checkReturnCode();
      bufferObjects.add(bufferObject);
      return bufferObject;
   }

   public _cl_mem createImage(int flags, int imageChannelOrder, int imageChannelDataType, int width, int height, Pointer hostPointer)
   {
      if (hostPointer != null)
         flags |= CL_MEM_USE_HOST_PTR;
      cl_image_format imageFormat = new cl_image_format();
      imageFormat.image_channel_order(imageChannelOrder);
      imageFormat.image_channel_data_type(imageChannelDataType);
      cl_image_desc imageDescription = new cl_image_desc();
      imageDescription.image_type(CL_MEM_OBJECT_IMAGE2D);
      imageDescription.image_width(width);
      imageDescription.image_height(height);
      imageDescription.image_depth(0);
      imageDescription.image_array_size(0);
      imageDescription.image_row_pitch(0);
      imageDescription.image_slice_pitch(0);
      imageDescription.num_mip_levels(0);
      imageDescription.num_samples(0);
      imageDescription.mem_object(null);
      _cl_mem image = clCreateImage(context, flags, imageFormat, imageDescription, hostPointer, returnCode);
      bufferObjects.add(image);
      checkReturnCode();
      return image;
   }

   public void enqueueWriteBuffer(_cl_mem bufferObject, long sizeInBytes, Pointer hostMemoryPointer)
   {
      /* Transfer data to memory buffer */
      int blockingWrite = CL_TRUE;
      int offset = 0;
      int numberOfEventsInWaitList = 0; // no events
      PointerPointer eventWaitList = null; // no events
      PointerPointer event = null; // no events
      checkReturnCode(clEnqueueWriteBuffer(commandQueue,
                                           bufferObject,
                                           blockingWrite,
                                           offset,
                                           sizeInBytes,
                                           hostMemoryPointer,
                                           numberOfEventsInWaitList,
                                           eventWaitList,
                                           event));
   }

   public void enqueueWriteImage(_cl_mem image, long imageWidth, long imageHeight, Pointer hostMemoryPointer)
   {
      /* Transfer data to memory buffer */
      int blockingWrite = CL_TRUE;
      origin.put(0, 0);
      origin.put(1, 0);
      origin.put(2, 0);
      region.put(0, imageWidth);
      region.put(1, imageHeight);
      region.put(2, 1);
      long inputRowPitch = 0;
      long inputSlicePitch = 0;
      int numberOfEventsInWaitList = 0; // no events
      PointerPointer eventWaitList = null; // no events
      PointerPointer event = null; // no events
      checkReturnCode(clEnqueueWriteImage(commandQueue,
                                          image,
                                          blockingWrite,
                                          origin,
                                          region,
                                          inputRowPitch,
                                          inputSlicePitch,
                                          hostMemoryPointer,
                                          numberOfEventsInWaitList,
                                          eventWaitList,
                                          event));
   }

   public void setKernelArgument(_cl_kernel kernel, int argumentIndex, _cl_mem bufferObject)
   {
      setKernelArgument(kernel, argumentIndex, pointerPointerSize, bufferObject);
   }

   public void setKernelArgument(_cl_kernel kernel, int argumentIndex, IntPointer intPointer)
   {
      setKernelArgument(kernel, argumentIndex, intPointerSize, intPointer);
   }

   /**
    * You can't pass booleans to OpenCL kernels, so we use an int.
    */
   public void setKernelArgument(_cl_kernel kernel, int argumentIndex, OpenCLBooleanParameter booleanParameter)
   {
      setKernelArgument(kernel, argumentIndex, intPointerSize, booleanParameter.getIntPointer());
   }

   private void setKernelArgument(_cl_kernel kernel, int argumentIndex, long argumentSize, Pointer bufferObject)
   {
      /* Set OpenCL kernel argument */
      tempPointerPointerForSetKernelArgument.put(bufferObject);
      checkReturnCode(clSetKernelArg(kernel, argumentIndex, argumentSize, tempPointerPointerForSetKernelArgument));
   }

   public void execute1D(_cl_kernel kernel, long workSizeX)
   {
      execute(kernel, 1, workSizeX, 0, 0);
   }

   public void execute2D(_cl_kernel kernel, long workSizeX, long workSizeY)
   {
      execute(kernel, 2, workSizeX, workSizeY, 0);
   }

   public void execute3D(_cl_kernel kernel, long workSizeX, long workSizeY, long workSizeZ)
   {
      execute(kernel, 3, workSizeX, workSizeY, workSizeZ);
   }

   /**
    * See https://www.khronos.org/registry/OpenCL/sdk/2.2/docs/man/html/clEnqueueNDRangeKernel.html
    */
   public void execute(_cl_kernel kernel, int numberOfWorkDimensions, long workSizeX, long workSizeY, long workSizeZ)
   {
      /* Enqueue OpenCL kernel execution */
      globalWorkSize.put(0, workSizeX);
      globalWorkSize.put(1, workSizeY);
      globalWorkSize.put(2, workSizeZ);
      SizeTPointer globalWorkOffset = null; // starts at (0,0,0)
      SizeTPointer localWorkSize = null; // auto mode?
      int numberOfEventsInWaitList = 0; // no events
      PointerPointer eventWaitList = null; // no events
      PointerPointer event = null; // no events
      checkReturnCode(clEnqueueNDRangeKernel(commandQueue,
                                             kernel,
                                             numberOfWorkDimensions,
                                             globalWorkOffset,
                                             globalWorkSize,
                                             localWorkSize,
                                             numberOfEventsInWaitList,
                                             eventWaitList,
                                             event));
   }

   public void enqueueReadBuffer(_cl_mem bufferObject, Pointer hostMemoryPointer)
   {
      enqueueReadBuffer(bufferObject, bufferObject.limit(), hostMemoryPointer);
   }

   public void enqueueReadBuffer(_cl_mem bufferObject, long sizeInBytes, Pointer hostMemoryPointer)
   {
      /* Transfer result from the memory buffer */
      checkReturnCode(clEnqueueReadBuffer(commandQueue, bufferObject, CL_TRUE, 0, sizeInBytes, hostMemoryPointer, 0, (PointerPointer) null, null));
   }

   public void enqueueReadImage(_cl_mem image, long imageWidth, long imageHeight, Pointer hostMemoryPointer)
   {
      /* Transfer result from the memory buffer */
      int blockingRead = CL_TRUE;
      origin.put(0, 0);
      origin.put(1, 0);
      origin.put(2, 0);
      region.put(0, imageWidth);
      region.put(1, imageHeight);
      region.put(2, 1);
      long inputRowPitch = 0;
      long inputSlicePitch = 0;
      int numberOfEventsInWaitList = 0; // no events
      PointerPointer eventWaitList = null; // no events
      PointerPointer event = null; // no events
      checkReturnCode(clEnqueueReadImage(commandQueue,
                                         image,
                                         blockingRead,
                                         origin,
                                         region,
                                         inputRowPitch,
                                         inputSlicePitch,
                                         hostMemoryPointer,
                                         numberOfEventsInWaitList,
                                         eventWaitList,
                                         event));
   }

   private void checkReturnCode(int returnCode)
   {
      this.returnCode.put(returnCode);
      if (returnCode != CL_SUCCESS) // duplicated to reduce stack trace height
      {
         String message = "OpenCL error code: " + returnCode + ": " + getReturnCodeString(returnCode);
         LogTools.error(1, message);
         throw new RuntimeException(message);
      }
   }

   private void checkReturnCode()
   {
      if (returnCode.get() != CL_SUCCESS)
      {
         String message = "OpenCL error code: " + returnCode.get() + ": " + getReturnCodeString(returnCode.get());
         LogTools.error(1, message);
         throw new RuntimeException(message);
      }
   }

   public void releaseBufferObject(_cl_mem bufferObject)
   {
      checkReturnCode(clReleaseMemObject(bufferObject));
      bufferObjects.remove(bufferObject);
   }

   public void destroy()
   {
      for (_cl_program program : programs)
         checkReturnCode(clReleaseProgram(program));
      programs.clear();
      for (_cl_kernel kernel : kernels)
         checkReturnCode(clReleaseKernel(kernel));
      kernels.clear();
      for (_cl_mem bufferObject : bufferObjects)
         checkReturnCode(clReleaseMemObject(bufferObject));
      bufferObjects.clear();
   }

   public int getReturnCode()
   {
      return returnCode.get();
   }

   private String getReturnCodeString(int returnCode)
   {
      switch (returnCode)
      {
         case OpenCL.CL_SUCCESS:
            return "CL_SUCCESS";
         case OpenCL.CL_DEVICE_NOT_FOUND:
            return "CL_DEVICE_NOT_FOUND";
         case OpenCL.CL_DEVICE_NOT_AVAILABLE:
            return "CL_DEVICE_NOT_AVAILABLE";
         case OpenCL.CL_COMPILER_NOT_AVAILABLE:
            return "CL_COMPILER_NOT_AVAILABLE";
         case OpenCL.CL_MEM_OBJECT_ALLOCATION_FAILURE:
            return "CL_MEM_OBJECT_ALLOCATION_FAILURE";
         case OpenCL.CL_OUT_OF_RESOURCES:
            return "CL_OUT_OF_RESOURCES";
         case OpenCL.CL_OUT_OF_HOST_MEMORY:
            return "CL_OUT_OF_HOST_MEMORY";
         case OpenCL.CL_PROFILING_INFO_NOT_AVAILABLE:
            return "CL_PROFILING_INFO_NOT_AVAILABLE";
         case OpenCL.CL_MEM_COPY_OVERLAP:
            return "CL_MEM_COPY_OVERLAP";
         case OpenCL.CL_IMAGE_FORMAT_MISMATCH:
            return "CL_IMAGE_FORMAT_MISMATCH";
         case OpenCL.CL_IMAGE_FORMAT_NOT_SUPPORTED:
            return "CL_IMAGE_FORMAT_NOT_SUPPORTED";
         case OpenCL.CL_BUILD_PROGRAM_FAILURE:
            return "CL_BUILD_PROGRAM_FAILURE";
         case OpenCL.CL_MAP_FAILURE:
            return "CL_MAP_FAILURE";
         case OpenCL.CL_MISALIGNED_SUB_BUFFER_OFFSET:
            return "CL_MISALIGNED_SUB_BUFFER_OFFSET";
         case OpenCL.CL_EXEC_STATUS_ERROR_FOR_EVENTS_IN_WAIT_LIST:
            return "CL_EXEC_STATUS_ERROR_FOR_EVENTS_IN_WAIT_LIST";
         case OpenCL.CL_COMPILE_PROGRAM_FAILURE:
            return "CL_COMPILE_PROGRAM_FAILURE";
         case OpenCL.CL_LINKER_NOT_AVAILABLE:
            return "CL_LINKER_NOT_AVAILABLE";
         case OpenCL.CL_LINK_PROGRAM_FAILURE:
            return "CL_LINK_PROGRAM_FAILURE";
         case OpenCL.CL_DEVICE_PARTITION_FAILED:
            return "CL_DEVICE_PARTITION_FAILED";
         case OpenCL.CL_KERNEL_ARG_INFO_NOT_AVAILABLE:
            return "CL_KERNEL_ARG_INFO_NOT_AVAILABLE";
         case OpenCL.CL_INVALID_VALUE:
            return "CL_INVALID_VALUE";
         case OpenCL.CL_INVALID_DEVICE_TYPE:
            return "CL_INVALID_DEVICE_TYPE";
         case OpenCL.CL_INVALID_PLATFORM:
            return "CL_INVALID_PLATFORM";
         case OpenCL.CL_INVALID_DEVICE:
            return "CL_INVALID_DEVICE";
         case OpenCL.CL_INVALID_CONTEXT:
            return "CL_INVALID_CONTEXT";
         case OpenCL.CL_INVALID_QUEUE_PROPERTIES:
            return "CL_INVALID_QUEUE_PROPERTIES";
         case OpenCL.CL_INVALID_COMMAND_QUEUE:
            return "CL_INVALID_COMMAND_QUEUE";
         case OpenCL.CL_INVALID_HOST_PTR:
            return "CL_INVALID_HOST_PTR";
         case OpenCL.CL_INVALID_MEM_OBJECT:
            return "CL_INVALID_MEM_OBJECT";
         case OpenCL.CL_INVALID_IMAGE_FORMAT_DESCRIPTOR:
            return "CL_INVALID_IMAGE_FORMAT_DESCRIPTOR";
         case OpenCL.CL_INVALID_IMAGE_SIZE:
            return "CL_INVALID_IMAGE_SIZE";
         case OpenCL.CL_INVALID_SAMPLER:
            return "CL_INVALID_SAMPLER";
         case OpenCL.CL_INVALID_BINARY:
            return "CL_INVALID_BINARY";
         case OpenCL.CL_INVALID_BUILD_OPTIONS:
            return "CL_INVALID_BUILD_OPTIONS";
         case OpenCL.CL_INVALID_PROGRAM:
            return "CL_INVALID_PROGRAM";
         case OpenCL.CL_INVALID_PROGRAM_EXECUTABLE:
            return "CL_INVALID_PROGRAM_EXECUTABLE";
         case OpenCL.CL_INVALID_KERNEL_NAME:
            return "CL_INVALID_KERNEL_NAME";
         case OpenCL.CL_INVALID_KERNEL_DEFINITION:
            return "CL_INVALID_KERNEL_DEFINITION";
         case OpenCL.CL_INVALID_KERNEL:
            return "CL_INVALID_KERNEL";
         case OpenCL.CL_INVALID_ARG_INDEX:
            return "CL_INVALID_ARG_INDEX";
         case OpenCL.CL_INVALID_ARG_VALUE:
            return "CL_INVALID_ARG_VALUE";
         case OpenCL.CL_INVALID_ARG_SIZE:
            return "CL_INVALID_ARG_SIZE";
         case OpenCL.CL_INVALID_KERNEL_ARGS:
            return "CL_INVALID_KERNEL_ARGS";
         case OpenCL.CL_INVALID_WORK_DIMENSION:
            return "CL_INVALID_WORK_DIMENSION";
         case OpenCL.CL_INVALID_WORK_GROUP_SIZE:
            return "CL_INVALID_WORK_GROUP_SIZE";
         case OpenCL.CL_INVALID_WORK_ITEM_SIZE:
            return "CL_INVALID_WORK_ITEM_SIZE";
         case OpenCL.CL_INVALID_GLOBAL_OFFSET:
            return "CL_INVALID_GLOBAL_OFFSET";
         case OpenCL.CL_INVALID_EVENT_WAIT_LIST:
            return "CL_INVALID_EVENT_WAIT_LIST";
         case OpenCL.CL_INVALID_EVENT:
            return "CL_INVALID_EVENT";
         case OpenCL.CL_INVALID_OPERATION:
            return "CL_INVALID_OPERATION";
         case OpenCL.CL_INVALID_GL_OBJECT:
            return "CL_INVALID_GL_OBJECT";
         case OpenCL.CL_INVALID_BUFFER_SIZE:
            return "CL_INVALID_BUFFER_SIZE";
         case OpenCL.CL_INVALID_MIP_LEVEL:
            return "CL_INVALID_MIP_LEVEL";
         case OpenCL.CL_INVALID_GLOBAL_WORK_SIZE:
            return "CL_INVALID_GLOBAL_WORK_SIZE";
         case OpenCL.CL_INVALID_PROPERTY:
            return "CL_INVALID_PROPERTY";
         case OpenCL.CL_INVALID_IMAGE_DESCRIPTOR:
            return "CL_INVALID_IMAGE_DESCRIPTOR";
         case OpenCL.CL_INVALID_COMPILER_OPTIONS:
            return "CL_INVALID_COMPILER_OPTIONS";
         case OpenCL.CL_INVALID_LINKER_OPTIONS:
            return "CL_INVALID_LINKER_OPTIONS";
         case OpenCL.CL_INVALID_DEVICE_PARTITION_COUNT:
            return "CL_INVALID_DEVICE_PARTITION_COUNT";
         case OpenCL.CL_INVALID_PIPE_SIZE:
            return "CL_INVALID_PIPE_SIZE";
         case OpenCL.CL_INVALID_DEVICE_QUEUE:
            return "CL_INVALID_DEVICE_QUEUE";
         case OpenCL.CL_INVALID_SPEC_ID:
            return "CL_INVALID_SPEC_ID";
         case OpenCL.CL_MAX_SIZE_RESTRICTION_EXCEEDED:
            return "CL_MAX_SIZE_RESTRICTION_EXCEEDED";
         default:
            return "Code not found";
      }
   }
}