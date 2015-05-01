## Overchan Android

Overchan Android (Meta Imageboard Client) is an application for browsing imageboards.

## Download APK

You can download the application [here](https://github.com/miku-nyan/Overchan-Android/releases).

## Building Source Code

### Dependencies

* [JDK 7](http://www.oracle.com/technetwork/java/javase/downloads/index.html) (JRE alone is not sufficient)
* [Android SDK](https://developer.android.com/sdk/index.html#Other)
* [Android NDK](https://developer.android.com/tools/sdk/ndk/index.html#Downloads)
* [Apache Ant](http://ant.apache.org/bindownload.cgi) or [Eclipse with ADT](http://developer.android.com/sdk/installing/installing-adt.html)

### Using Ant

Open the source code directory and run (in the command line):

`ant -Dsdk.dir=/path/to/android-sdk -Dndk.dir=/path/to/android-ndk debug`

The .apk file (`bin/Overchan-debug.apk`) will be signed with the debug key.

*Note for Windows platforms:*
NDK path cannot contain any spaces but you can use the short name of the path (example: `C:\Program Files` → `C:\PROGRA~1`). The short directory name can be found using `dir /x` (example: `dir /x c:\`)

### Using Eclipse with ADT

Just import the project into your workspace (File → Import → Android → Existing Android code into workspace → select the folder).

### Android Studio/IntelliJ IDEA

You may use Android Studio/IntelliJ IDEA at your own risk. If you need to keep the directory structure (for further contributing) put this [build.gradle](https://yadi.sk/d/QxOq3anxg94NJ) file into the project directory before the import.

## License

Overchan Android is licensed under the [GPLv3](http://www.gnu.org/licenses/gpl-3.0.txt).
