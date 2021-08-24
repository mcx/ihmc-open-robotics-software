package us.ihmc.perception;

import org.bytedeco.javacpp.*;
import org.bytedeco.opencl.*;

import java.nio.file.Paths;
import java.util.ArrayList;

import static org.bytedeco.opencl.global.OpenCL.*;

/**
 * Reference: https://www.khronos.org/registry/OpenCL/sdk/2.2/docs/man/html/
 */
public class OpenCLContext
{
   private _cl_platform_id platforms;
   private _cl_device_id devices;
   private _cl_context context;
   private _cl_command_queue commandQueue;
   private final IntPointer numberOfDevices = new IntPointer(1);
   private final IntPointer numberOfPlatforms = new IntPointer(1);
   private final IntPointer nativeReturnCode = new IntPointer(1);
   private int returnCode = 0;
   private final ArrayList<_cl_program> programs = new ArrayList<>();
   private final ArrayList<_cl_kernel> kernels = new ArrayList<>();
   private final ArrayList<_cl_mem> bufferObjects = new ArrayList<>();
   private final SizeTPointer globalWorkSize = new SizeTPointer(0, 0, 0);
   private final SizeTPointer localWorkSize = new SizeTPointer(0, 0, 0);

   public void create()
   {
      /* Get platform/device information */
      returnCode = clGetPlatformIDs(1, platforms, numberOfPlatforms);
      returnCode = clGetDeviceIDs(platforms, CL_DEVICE_TYPE_GPU, 1, devices, numberOfDevices);

      /* Create OpenCL Context */
      context = clCreateContext(null, 1, devices, null, null, nativeReturnCode);

      /* Create Command Queue */
      IntPointer properties = new IntPointer(new int[] {0});
      commandQueue = clCreateCommandQueueWithProperties(context, devices, properties, nativeReturnCode);
   }

   public _cl_kernel loadProgramAndCreateKernel(String programName)
   {
      String sourceAsString = OpenCLTools.readFile(Paths.get("openCL", programName + ".cl"));

      /* Create Kernel program from the read in source */
      _cl_program program = clCreateProgramWithSource(context, 1, new PointerPointer(sourceAsString), new SizeTPointer(1).put(sourceAsString.length()), nativeReturnCode);
      programs.add(program);

      /* Build Kernel Program */
      returnCode = clBuildProgram(program, 1, devices, null, null, null);

      /* Create OpenCL Kernel */
      _cl_kernel kernel = clCreateKernel(program, programName, nativeReturnCode);
      kernels.add(kernel);
      return kernel;
   }

   public void setupKernelArgument(_cl_kernel kernel, int argumentIndex, long sizeInBytes, Pointer hostMemoryPointer)
   {
      _cl_mem bufferObject = createBufferObject(sizeInBytes);
      enqueueWriteBuffer(bufferObject, sizeInBytes, hostMemoryPointer);
      setKernelArgument(kernel, argumentIndex, bufferObject);
   }

   public _cl_mem createBufferObject(long sizeInBytes)
   {
      _cl_mem bufferObject = clCreateBuffer(context, CL_MEM_READ_WRITE, sizeInBytes, null, nativeReturnCode);
      bufferObjects.add(bufferObject);
      return bufferObject;
   }

   public void enqueueWriteBuffer(_cl_mem bufferObject, long sizeInBytes, Pointer hostMemoryPointer)
   {
      /* Transfer data to memory buffer */
      returnCode = clEnqueueWriteBuffer(commandQueue, bufferObject, CL_TRUE, 0, sizeInBytes, hostMemoryPointer, 0, (PointerPointer) null, null);
   }

   public void setKernelArgument(_cl_kernel kernel, int argumentIndex, _cl_mem bufferObject)
   {
      /* Set OpenCL kernel argument */
      returnCode = clSetKernelArg(kernel, argumentIndex, Loader.sizeof(PointerPointer.class), new PointerPointer(1).put(bufferObject));
   }

   public void execute(_cl_kernel kernel, long workSize)
   {
      /* Execute OpenCL kernel */
      globalWorkSize.put(workSize);
      localWorkSize.put(workSize);
      returnCode = clEnqueueNDRangeKernel(commandQueue, kernel, 1, null, globalWorkSize, localWorkSize, 0, (PointerPointer) null, null);
   }

   public void enqueueReadBuffer(_cl_mem bufferObject, long sizeInBytes, Pointer hostMemoryPointer)
   {
      /* Transfer result from the memory buffer */
      returnCode = clEnqueueReadBuffer(commandQueue, bufferObject, CL_TRUE, 0, sizeInBytes, hostMemoryPointer, 0, (PointerPointer) null, null);
   }

   public void destroy()
   {
      returnCode = clFlush(commandQueue);
      returnCode = clFinish(commandQueue);
      for (_cl_program program : programs)
         returnCode = clReleaseProgram(program);
      for (_cl_kernel kernel : kernels)
         returnCode = clReleaseKernel(kernel);
      for (_cl_mem bufferObject : bufferObjects)
         returnCode = clReleaseMemObject(bufferObject);
      returnCode = clReleaseCommandQueue(commandQueue);
      returnCode = clReleaseContext(context);
   }

   public IntPointer getNativeReturnCode()
   {
      return nativeReturnCode;
   }
}
