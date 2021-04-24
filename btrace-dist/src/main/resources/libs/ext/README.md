# BTrace Extension Repository

## !Experimental feature!

This directory is the default location for installed extensions. 
Extensions are simple JAR files with at least the following manifest attributes set:

* `BTrace-Extension-ID` - a unique extension ID
* `BTrace-Extension-Version` - extension version 
* `BTrace-Extension-Exports` - comma separated list of packages 'exported' by the extension

Each extension JAR file must contain all required dependencies. 
It is not possible to use classes across extensions.