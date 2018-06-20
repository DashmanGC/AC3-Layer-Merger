# Ace Combat 3 Layer Merger for TIM files v1.2

## What's new?  

Supports relative and absolute paths, correctly places
the number of the CLUT in the header (0x4C)

## How to use this thing.

First of all, this is a Java applet. If you don't have Java, install
the latest JRE. Second, this is a command line application. To execute
it, you'll need to open up a console/shell window, get to the folder
where the program is and execute this:

java -jar ac3lm.jar <TIM_layer1> <TIM_layer2> <output_file>

## Stuff to note.

* TIM_layer1 and TIM_layer2 are the TIM files that you want to merge
  into the TIM with two CLUTs (output_file).

* DON'T try to merge TIM files with more than one CLUT, the result
  won't be pretty.

* This program doesn't support batches, sorry.

* Supports relative and absolute paths! 

* No more OR operation. This time it checks the indexed colours of
  both layers and writes the index that is colour in one layer and
  transparent on the other, as simple as that.
