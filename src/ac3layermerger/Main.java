/*
 * Copyright (C) 2018 Dashman
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package ac3layermerger;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.logging.Level;
import java.util.logging.Logger;


/**
 * @author Jonatan
 */
public class Main {

    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) {
        String layer1;
        String layer2;
        String destination;

        String appname = System.getProperty("sun.java.command");

        if (args.length == 0 || (args.length == 1 && args[0].equals("-h"))) {
            System.out.println("Ace Combat 3 Layer Merger for TIM files v1.2");
            System.out.println("USE: java -jar " + appname + " <TIM_layer1> <TIM_layer2> <result_TIM>");
            return;
        }

        if (args.length != 3) {
            System.out.println("ERROR: Wrong number of parameters: " + args.length);
            System.out.println("USE: java -jar " + appname + " <TIM_layer1> <TIM_layer2> <result_TIM>");
            return;
        }

        Path pathLayer1 = Paths.get(args[0]).toAbsolutePath();
        Path pathLater2 = Paths.get(args[1]).toAbsolutePath();
        Path pathDestination = Paths.get(args[2]).toAbsolutePath();

        if (args[0].equals(args[1])) {
            System.out.println("ERROR: The original and edited files can't be the same!");
            return;
        }

        if (!args[0].toLowerCase().endsWith(".tim") || !args[1].toLowerCase().endsWith(".tim")) {
            System.out.println("ERROR: You have to use this with .tim files!");
            return;
        }

        mergeTIM(pathLayer1, pathLater2, pathDestination);
    }

    /**
     * @param pathLayer1
     * @param pathLayer2
     * @param pathDestination
     */
    public static void mergeTIM(Path pathLayer1, Path pathLayer2, Path pathDestination) {
        try {
            // Open the TIM files with the layers, exit if a file doesn't exist
            RandomAccessFile fileLayer1 = new RandomAccessFile(pathLayer1.toString(), "r");
            RandomAccessFile fileLayer2 = new RandomAccessFile(pathLayer2.toString(), "r");

            // Stop if the size is different
            if (fileLayer1.length() != fileLayer2.length()) {
                fileLayer1.close();
                fileLayer2.close();
                System.err.println("ERROR: The TIM files have different sizes.");
                return;
            }

            // Read the header of layer1 (52 bytes) and modify the number of CLUTs to 2 (byte 18)
            byte[] header_l1 = new byte[52];

            fileLayer1.read(header_l1);
            header_l1[18] = 2;
            //fix clut number
            header_l1[8] = 0x4c;

            // Read the extra 12 bytes after the CLUT of layer1 (it contains Img Org X, Img Org Y and two more things...  I think)
            byte[] extra_l1 = new byte[12];
            fileLayer1.read(extra_l1);

            // Read the CLUT of layer 2 (offset 20, 32 bytes)
            byte[] clut_l2 = new byte[32];
            fileLayer2.skipBytes(20);
            fileLayer2.read(clut_l2);
            fileLayer2.skipBytes(12); // skip the extra bytes in layer 2

            // Read the image data from both layers (file length - 64) *** FILES MUST ONLY HAVE 1 CLUT, OTHERWISE THIS FAILS!
            long datasize = fileLayer1.length() - 64;
            byte[] l1_data = new byte[(int) datasize];
            byte[] l2_data = new byte[(int) datasize];

            fileLayer1.read(l1_data);
            fileLayer2.read(l2_data);

            fileLayer1.close();
            fileLayer2.close();

            // New image data = layer1 data OR layer2 data
            byte[] new_data = new byte[(int) datasize];

            for (int i = 0; i < datasize; i++) {
                // Java casts bytes as int for operations. We have to do a little trick here
                //new_data[i] = l1_data[i] | l2_data[i];
                //Integer notabyte = l1_data[i] | l2_data[i];
                // Let's try with AND
                //Integer notabyte = l1_data[i] & l2_data[i];
                // Let's try a simple sum
                //Integer notabyte = l1_data[i] + l2_data[i];
                // And with Exclusive OR
                //Integer notabyte = l1_data[i] ^ l2_data[i];
                //new_data[i] = notabyte.byteValue();
                new_data[i] = getNewData(l1_data[i], l2_data[i]);
            }

            // Open the destination file, exit if we can't create the file
            RandomAccessFile f3 = new RandomAccessFile(pathDestination.toString(), "rw");

            // Write in dest: modified header of layer1 + CLUT of layer2 + extra_l1 + new image data
            f3.write(header_l1);
            f3.write(clut_l2);
            f3.write(extra_l1);
            f3.write(new_data);

            f3.close();

            System.out.println("SUCCESS: File " + pathDestination.toString() + " created successfully!");

        } catch (FileNotFoundException ex) {
            Logger.getLogger(Main.class.getName()).log(Level.SEVERE, null, ex);
            System.err.println("ERROR: Files not found.");
        } catch (IOException ex) {
            Logger.getLogger(Main.class.getName()).log(Level.SEVERE, null, ex);
            System.err.println("ERROR: Could not read from files.");
        }
    }

    /**
     * @param b1
     * @param b2
     * @return
     */
    public static byte getNewData(byte b1, byte b2) {
        Integer result = 0x00;
        int color_l1_h1 = 0;
        int color_l2_h1 = 0;
        int color_l1_h2 = 0;
        int color_l2_h2 = 0;

        // First, we have to split b1 and b2 in half.
        // For example, b1 can be 0xc0, that would give us 0xc and 0x0
        // Each half indicates an indexed pixel in the image
        int b1_h1 = b1 & 0xf0;
        b1_h1 = b1_h1 >> 4;
        int b1_h2 = b1 & 0x0f;
        int b2_h1 = b2 & 0xf0;
        b2_h1 = b2_h1 >> 4;
        int b2_h2 = b2 & 0x0f;

        // Now we have to determine the colour that is supposed to appear on each layer
        // For layer 1:
        // 0, 4, 8 and c are COLOR 1
        // 1, 5, 9 and d are COLOR 2
        // 2, 6, a and e are COLOR 3
        // 3, 7, b and f are COLOR 4
        color_l1_h1 = b1_h1 % 4;
        color_l1_h2 = b1_h2 % 4;

        // For layer 2:
        // 0 to 3 are COLOR 1
        // 4 to 7 are COLOR 2
        // 8 to b are COLOR 3
        // c to f are COLOR 4
        if (b2_h1 < 8) {
            if (b2_h1 < 4)
                color_l2_h1 = 0;
            else
                color_l2_h1 = 1;
        } else {
            if (b2_h1 < 12)
                color_l2_h1 = 2;
            else
                color_l2_h1 = 3;
        }

        if (b2_h2 < 8) {
            if (b2_h2 < 4)
                color_l2_h2 = 0;
            else
                color_l2_h2 = 1;
        } else {
            if (b2_h2 < 12)
                color_l2_h2 = 2;
            else
                color_l2_h2 = 3;
        }


        // Now that we know which colours we want in each layer,
        // we give the final pixel the value that gives the proper colours
        int byte_L = color_l1_h1 + 4 * color_l2_h1;
        byte_L = byte_L << 4;
        int byte_R = color_l1_h2 + 4 * color_l2_h2;
        /*
        // We start with the first half (the rightmost byte)
        switch (color_l1_h2){
            case 0:
                switch (color_l2_h2){
                    case 0:
                        // Do nothing, it's already 0
                        break;
                    case 1:
                        result = result | 0x04;
                        break;
                    case 2:
                        result = result | 0x08;
                        break;
                    case 3:
                        result = result | 0x0c;
                        break;
                }
                break;
            case 1:
                switch (color_l2_h2){
                    case 0:
                        result = result | 0x01;
                        break;
                    case 1:
                        result = result | 0x05;
                        break;
                    case 2:
                        result = result | 0x09;
                        break;
                    case 3:
                        result = result | 0x0d;
                        break;
                }
                break;
            case 2:
                switch (color_l2_h2){
                    case 0:
                        result = result | 0x04;
                        break;
                    case 1:
                        break;
                    case 2:
                        break;
                    case 3:
                        break;
                }
                break;
            case 3:
                switch (color_l2_h2){
                    case 0:
                        break;
                    case 1:
                        break;
                    case 2:
                        break;
                    case 3:
                        break;
                }
                break;
        }*/

        // Write the bytes in result
        result = result | byte_L;
        result = result | byte_R;

        /*
        System.out.println("Byte 1: " + String.format("0x%02X", b1) + " Byte 2: " + String.format("0x%02X", b2) +
                " Color 1-L: "  + color_l1_h1 + " Color 2-L: "  + color_l2_h1 + " Color 1-R: " + color_l1_h2 +
                 " Color 2-R: " + color_l2_h2 + " Result: " + String.format("0x%02X", result));*/

        return result.byteValue();
    }
}
