package us.ihmc.avatar.multiContact;

import toolbox_msgs.msg.dds.KinematicsToolboxOutputStatus;
import gnu.trove.list.array.TIntArrayList;
import us.ihmc.tools.io.WorkspacePathTools;

import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.io.File;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;

public class MultiContactScriptMutator
{
   public MultiContactScriptMutator()
   {
//      reverseValkyrieScript();
      setShoeWristsToZero();
   }

   private void reverseValkyrieScript()
   {
      Path currentDirectory = WorkspacePathTools.handleWorkingDirectoryFuzziness("ihmc-open-robotics-software")
                                                .resolve("valkyrie/src/main/resources/multiContact/scripts")
                                                .toAbsolutePath()
                                                .normalize();
      System.out.println(currentDirectory);

      JFileChooser fileChooser = new JFileChooser(currentDirectory.toFile());
      fileChooser.setFileFilter(new FileNameExtensionFilter("JSON log", "json"));

      int chooserState = fileChooser.showOpenDialog(null);
      if (chooserState != JFileChooser.APPROVE_OPTION)
         return;

      File selectedFile = fileChooser.getSelectedFile();
      MultiContactScriptReader scriptReader = new MultiContactScriptReader();
      if (!scriptReader.loadScript(selectedFile))
         return;

      List<KinematicsToolboxSnapshotDescription> script = scriptReader.getAllItems();
      Collections.reverse(script);

      MultiContactScriptWriter scriptWriter = new MultiContactScriptWriter();
      Path folderPath = WorkspacePathTools.handleWorkingDirectoryFuzziness("ihmc-virtual-reality-user-interface");
      folderPath = folderPath.getParent().resolve("ihmc-open-robotics-software/valkyrie/src/main/resources/multiContact/scripts");
      Path path = folderPath.toAbsolutePath().normalize();

      String originalFilename = selectedFile.getName();
      String newFilename = originalFilename.substring(0, originalFilename.length() - ".json".length()) + "_reversed.json";
      scriptWriter.startNewScript(new File(path.toFile(), newFilename), false);
      for (int i = 0; i < script.size(); i++)
      {
         scriptWriter.recordConfiguration(script.get(i));
      }

      scriptWriter.writeScript();
   }

   private void setShoeWristsToZero()
   {
      Path currentDirectory = WorkspacePathTools.handleWorkingDirectoryFuzziness("shoe").resolve("optimus-simulation/src/main/resources/multiContact/scripts").toAbsolutePath().normalize();

      JFileChooser fileChooser = new JFileChooser(currentDirectory.toFile());
      fileChooser.setFileFilter(new FileNameExtensionFilter("JSON log", "json"));

      int chooserState = fileChooser.showOpenDialog(null);
      if (chooserState != JFileChooser.APPROVE_OPTION)
         return;

      File selectedFile = fileChooser.getSelectedFile();
      MultiContactScriptReader scriptReader = new MultiContactScriptReader();
      if (!scriptReader.loadScript(selectedFile))
         return;

      TIntArrayList wristJointIndices = new TIntArrayList();
      wristJointIndices.add(6);
      wristJointIndices.add(7);
      wristJointIndices.add(14);
      wristJointIndices.add(15);

      List<KinematicsToolboxSnapshotDescription> script = scriptReader.getAllItems();
      for (int i = 0; i < script.size(); i++)
      {
         KinematicsToolboxOutputStatus ikSolution = script.get(i).getIkSolution();

         for (int j = 0; j < wristJointIndices.size(); j++)
         {
            ikSolution.getDesiredJointAngles().set(wristJointIndices.get(j), 0.0f);
         }
      }

      MultiContactScriptWriter scriptWriter = new MultiContactScriptWriter();
      Path folderPath = WorkspacePathTools.handleWorkingDirectoryFuzziness("shoe");
      folderPath = folderPath.getParent().resolve("shoe/optimus-simulation/src/main/resources/multiContact/scripts");
      Path path = folderPath.toAbsolutePath().normalize();

      String originalFilename = selectedFile.getName();
      String newFilename = originalFilename.substring(0, originalFilename.length() - ".json".length()) + "_wristsZeroed.json";
      scriptWriter.startNewScript(new File(path.toFile(), newFilename), false);
      for (int i = 0; i < script.size(); i++)
      {
         scriptWriter.recordConfiguration(script.get(i));
      }

      scriptWriter.writeScript();
   }

   public static void main(String[] args)
   {
      new MultiContactScriptMutator();
   }
}
