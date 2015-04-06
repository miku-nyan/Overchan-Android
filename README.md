## Overchan Android

Overchan Android (Meta Imageboard Client) is an application for browsing imageboards.

## Imageboards support

Currently support: [iichan.hk](http://iichan.hk/), [410chan.org](http://410chan.org), [2ch.hk](https://2ch.hk/), [4chan.org](https://www.4chan.org).

## Download APK

You can download the application here:
https://github.com/......

## Building Source Code

### Dependencies

* [JDK 7](http://www.oracle.com/technetwork/java/javase/downloads/index.html) (JRE alone is not sufficient)
* [Android SDK](https://developer.android.com/sdk/index.html#Other)
* [Android NDK](https://developer.android.com/tools/sdk/ndk/index.html#Downloads)
* [Apache Ant](http://ant.apache.org/bindownload.cgi) or [Eclipse with ADT](http://developer.android.com/sdk/installing/installing-adt.html)

### Using Ant

Open the source code directory and run (in the command line):

`ant -Dsdk.dir=/path/to/android-sdk -Dndk.dir=/path/to/android-ndk debug`

*Note for Windows platforms:*
NDK path cannot contain any spaces but you can use the short name of the path (example: `C:\Program Files` → `C:\PROGRA~1`). The short directory name can be found usings `dir /x` (example: `dir /x c:\`)

The .apk file (`bin/Overchan-debug.apk`) will be signed with the debug key.

### Using Eclipse

Just import the project into your workspace (File → Import → Android → Existing Android code into workspace → select the folder).

## License

Overchan Android is licensed under the [GPLv3](http://www.gnu.org/licenses/gpl-3.0.txt).
