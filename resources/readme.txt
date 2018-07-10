REQUIRED LIBRARIES FOR EACH PLUGIN:

Spandex_:
  - commons-lang-2.6.jar
  - MorphoLibJ_-1.3.1.jar
  - parallel_iterative_deconvolution-1.9.jar
  
roiMonitor_:
  - commons-lang-2.6.jar
  
apply_LUT:
  NO ADDITIONAL LIBRARIES REQUIRED
  
generate_LUT:
  - commons-math3-3.6.1.jar
  - commons-lang-2.6.jar
  - jep-3.8.0.jar

JEP INSTALLATION:

To install correct version of JEP install using setup.py from ZIP file with:
>> python setup.py build install
This uses Python 2.7.10.
For more information (different versions,etc.) go here: https://github.com/ninia/jep/wiki/Getting-Started
Install a JDK if you don't have one and set the directory as JAVA_HOME in your environment variables.

JAVA RUNTIME ENVIRONMENT
Extract jre.zip to micromanager directory and overwrite existing JRE
Inst
