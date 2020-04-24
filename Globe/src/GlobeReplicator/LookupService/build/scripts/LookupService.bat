@rem
@rem Copyright 2015 the original author or authors.
@rem
@rem Licensed under the Apache License, Version 2.0 (the "License");
@rem you may not use this file except in compliance with the License.
@rem You may obtain a copy of the License at
@rem
@rem      https://www.apache.org/licenses/LICENSE-2.0
@rem
@rem Unless required by applicable law or agreed to in writing, software
@rem distributed under the License is distributed on an "AS IS" BASIS,
@rem WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
@rem See the License for the specific language governing permissions and
@rem limitations under the License.
@rem

@if "%DEBUG%" == "" @echo off
@rem ##########################################################################
@rem
@rem  LookupService startup script for Windows
@rem
@rem ##########################################################################

@rem Set local scope for the variables with windows NT shell
if "%OS%"=="Windows_NT" setlocal

set DIRNAME=%~dp0
if "%DIRNAME%" == "" set DIRNAME=.
set APP_BASE_NAME=%~n0
set APP_HOME=%DIRNAME%..

@rem Resolve any "." and ".." in APP_HOME to make it shorter.
for %%i in ("%APP_HOME%") do set APP_HOME=%%~fi

@rem Add default JVM options here. You can also use JAVA_OPTS and LOOKUP_SERVICE_OPTS to pass JVM options to this script.
set DEFAULT_JVM_OPTS=

@rem Find java.exe
if defined JAVA_HOME goto findJavaFromJavaHome

set JAVA_EXE=java.exe
%JAVA_EXE% -version >NUL 2>&1
if "%ERRORLEVEL%" == "0" goto init

echo.
echo ERROR: JAVA_HOME is not set and no 'java' command could be found in your PATH.
echo.
echo Please set the JAVA_HOME variable in your environment to match the
echo location of your Java installation.

goto fail

:findJavaFromJavaHome
set JAVA_HOME=%JAVA_HOME:"=%
set JAVA_EXE=%JAVA_HOME%/bin/java.exe

if exist "%JAVA_EXE%" goto init

echo.
echo ERROR: JAVA_HOME is set to an invalid directory: %JAVA_HOME%
echo.
echo Please set the JAVA_HOME variable in your environment to match the
echo location of your Java installation.

goto fail

:init
@rem Get command-line arguments, handling Windows variants

if not "%OS%" == "Windows_NT" goto win9xME_args

:win9xME_args
@rem Slurp the command line arguments.
set CMD_LINE_ARGS=
set _SKIP=2

:win9xME_args_slurp
if "x%~1" == "x" goto execute

set CMD_LINE_ARGS=%*

:execute
@rem Setup the command line

set CLASSPATH=%APP_HOME%\lib\LookupService.jar;%APP_HOME%\lib\akka-http_2.12-10.1.11.jar;%APP_HOME%\lib\akka-stream_2.12-2.5.26.jar;%APP_HOME%\lib\jackson-module-scala_2.12-2.10.3.jar;%APP_HOME%\lib\akka-http-core_2.12-10.1.11.jar;%APP_HOME%\lib\akka-actor_2.12-2.5.26.jar;%APP_HOME%\lib\akka-protobuf_2.12-2.5.26.jar;%APP_HOME%\lib\ssl-config-core_2.12-0.3.8.jar;%APP_HOME%\lib\akka-parsing_2.12-10.1.11.jar;%APP_HOME%\lib\scala-java8-compat_2.12-0.8.0.jar;%APP_HOME%\lib\scala-parser-combinators_2.12-1.1.2.jar;%APP_HOME%\lib\scala-library-2.12.10.jar;%APP_HOME%\lib\reactive-streams-1.0.2.jar;%APP_HOME%\lib\jackson-module-paranamer-2.10.3.jar;%APP_HOME%\lib\jackson-databind-2.10.3.jar;%APP_HOME%\lib\jackson-core-2.10.3.jar;%APP_HOME%\lib\jackson-annotations-2.10.3.jar;%APP_HOME%\lib\config-1.3.3.jar;%APP_HOME%\lib\paranamer-2.8.jar

@rem Execute LookupService
"%JAVA_EXE%" %DEFAULT_JVM_OPTS% %JAVA_OPTS% %LOOKUP_SERVICE_OPTS%  -classpath "%CLASSPATH%" com.github.arucard21.globe.replicator.lookupservice.LSApplication %CMD_LINE_ARGS%

:end
@rem End local scope for the variables with windows NT shell
if "%ERRORLEVEL%"=="0" goto mainEnd

:fail
rem Set variable LOOKUP_SERVICE_EXIT_CONSOLE if you need the _script_ return code instead of
rem the _cmd.exe /c_ return code!
if  not "" == "%LOOKUP_SERVICE_EXIT_CONSOLE%" exit 1
exit /b 1

:mainEnd
if "%OS%"=="Windows_NT" endlocal

:omega
