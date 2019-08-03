# SudokuSolverX

SudokuSolverX is an Android app for solving Sudoku puzzles live via the phone camera.

I thought this would be an interesting project after doing a Sudoku solver leetcode problem. The app uses the OpenCV library to detect the sudoku grid and prepare the image for OCR, e.g. remove the grid lines. The Tesseract library is also used to perform the OCR. This was the result of a few days exploration of OpenCV, Tesseract and Android, so it is not super robust. If there is any interest then I will improve the quality and publish it to the Android Play Store. More details at https://adamk.org/SudokuSolverX

## Building

This requires OpenCV library (I used OpenCV 4.1). I left the OpenCV directory out of git because it was so big. You should be able to recreate it by downoading it from:
https://sourceforge.net/projects/opencvlibrary/files/opencv-android/
Then import the OpenCV library with the following steps: 

 - Import OpenCV into the project: File -> New -> Import Module. 
 - Select downloaded Opencv directory opencv/sdk  (Not opencv/sdk/java in most tutorials as that will generate errors for >= opencv 4.1):
https://stackoverflow.com/questions/55835542/working-with-opencv-in-the-new-android-studio-3-4/55937338#55937338
 - Then type a name for the module, the default is ":sdk" but I chose "openCVLibrary410"

You should now be able to build through the Android Studio Build menu. 

## Installing

 - Extract tessdata.zip to the phone’s Download directory (sdcard/Download). 
 - Copy SudokuSolverX.apk above to the device and run it to install. You might need to temporarily disable Play Protect while installing.

## Running 

Now run the SudokuSolverX app and move phone over a sudoku puzzle until you get blue and green numbers above every box in the puzzle. It might take some moving around to get the box straight enough for the detection to succeed. If not successful then you will not see the blue box surround the grid, you will just see red boxes.

