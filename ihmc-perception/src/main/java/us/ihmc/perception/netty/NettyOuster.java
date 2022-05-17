package us.ihmc.perception.netty;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.DatagramPacket;
import io.netty.channel.socket.nio.NioDatagramChannel;
import org.bytedeco.opencv.global.opencv_core;
import org.bytedeco.opencv.opencv_core.Mat;
import us.ihmc.log.LogTools;
import us.ihmc.perception.BytedecoImage;

import java.io.*;
import java.net.Socket;
import java.net.UnknownHostException;
import java.nio.ByteOrder;

/**
 * Ouster Firmware User Manual: https://data.ouster.io/downloads/software-user-manual/firmware-user-manual-v2.3.0.pdf
 *
 * To test, use the GNU netcat command:
 * netcat -ul 7502
 */
public class NettyOuster
{
   public static final int TCP_PORT = 7501;
   public static final int UDP_PORT = 7502;

   // -- LIDAR Information --
   private int pixelsPerColumn;
   private int columnsPerFrame;
   private int columnsPerPacket;
   private int[] pixelShift;
   // -- End LIDAR Information --

   private EventLoopGroup group;
   private Bootstrap bootstrap;
   private BytedecoImage image;

   public NettyOuster()
   {
      String jsonResponse = "";
      try (Socket socket = new Socket("192.168.244.2", TCP_PORT)) { //TODO IP should not be hardcoded (wait for UDP to get?)

         OutputStream output = socket.getOutputStream();
         PrintWriter writer = new PrintWriter(output, true);
         writer.println("get_lidar_data_format");

         InputStream input = socket.getInputStream();
         BufferedReader reader = new BufferedReader(new InputStreamReader(input));

         jsonResponse = reader.readLine();
      } catch (UnknownHostException ex) {
         LogTools.error("Ouster host could not be found.");
         return;
      } catch (IOException ex) {
         LogTools.error(ex.getMessage());
         LogTools.error(ex.getStackTrace());
      }

      try
      {
         ObjectMapper mapper = new ObjectMapper();
         JsonNode root = mapper.readTree(jsonResponse);

         pixelsPerColumn = root.get("pixels_per_column").asInt();
         columnsPerFrame = root.get("columns_per_frame").asInt();
         columnsPerPacket = root.get("columns_per_packet").asInt();
         pixelShift = new int[pixelsPerColumn];

         JsonNode pShift = root.get("pixel_shift_by_row");
         for(int i = 0; i < pixelsPerColumn; i++) {
            pixelShift[i] = pShift.get(i).asInt();
         }
      }
      catch (JsonProcessingException ex)
      {
         LogTools.error(ex.getMessage());
         return;
      }

      build();
   }

   private void build()
   {
      image = new BytedecoImage(columnsPerFrame, pixelsPerColumn, opencv_core.CV_32FC1);
      image.getBytedecoOpenCVMat().setTo(new Mat(0.0f)); //Initialize matrix to 0

      group = new NioEventLoopGroup();
      bootstrap = new Bootstrap();
      bootstrap.group(group).channel(NioDatagramChannel.class).handler(new SimpleChannelInboundHandler<DatagramPacket>()
      {
         @Override
         protected void channelRead0(ChannelHandlerContext context, DatagramPacket packet)
         {
            ByteBuf bufferedData = packet.content().readBytes(packet.content().capacity());
            bufferedData = bufferedData.order(ByteOrder.LITTLE_ENDIAN); //Ouster is little endian

            int i = 0;
            for (int colNumber = 0; colNumber < columnsPerPacket; colNumber++)
            {
               i += 8; //Timestamp
               int measurementID = (int) extractValue(bufferedData, i, 2);
               i += 8; //Measurement ID (above), Frame ID, Encoder Count

               long[] range = new long[pixelsPerColumn];
               for (int blockID = 0; blockID < pixelsPerColumn; blockID++)
               { //Note that blockID is useful data here
                  range[blockID] = extractValue(bufferedData, i, 4);
                  i += 12; //Range, and other values we don't care about
               }

               boolean dataOkay = extractValue(bufferedData, i, 4) == 0xFFFFFFFFL;
               i += 4;

               if (dataOkay)
               {
                  for (int k = 0; k < 64; k++)
                  {
                     float rangeScaled = range[k] / 1000.0F;
                     if (rangeScaled > 30.0)
                     {
                        rangeScaled = 0.0f;
                     }

                     //Calculate column by adding the reported row pixel shift to the measurement ID, and then adjusting for over/underflow
                     int column = (measurementID + pixelShift[k]) % columnsPerFrame;
                     image.getBytedecoOpenCVMat().ptr(k, column).putFloat(rangeScaled);
                  }
               }
            }

            bufferedData.release();
         }
      });

      int lidarPacketSize = columnsPerPacket * (16 + (pixelsPerColumn * 12) + 4);
      bootstrap.option(ChannelOption.RCVBUF_ALLOCATOR, new FixedRecvByteBufAllocator(lidarPacketSize));
   }

   /**
    * Java doesn't have support for unsigned 32-bit integers* so we have to use a long instead
    */
   private long extractValue(ByteBuf data, int index, int num)
   {
      int shift = index % 4;
      int modIndex = index - (index % 4);
      long val = data.getUnsignedInt(modIndex);
      val >>= shift;
      switch (num)
      {
         case 1:
            val = val & 0xFF;
            break;
         case 2:
            val = val & 0xFFFF;
            break;
         case 4:
            break;
         case 8:
            val += data.getUnsignedInt(modIndex + 4) << 32;
            break;
         default:
            return -1;
      }

      return val;
   }

   public void start()
   {
      bootstrap.bind(UDP_PORT);
   }

   public void stop()
   {
      group.shutdownGracefully();
   }

   public int getImageWidth()
   {
      return columnsPerFrame;
   }

   public int getImageHeight()
   {
      return pixelsPerColumn;
   }

   public BytedecoImage getBytedecoImage()
   {
      return image;
   }
}
