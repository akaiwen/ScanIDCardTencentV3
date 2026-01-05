@echo off
set DIR=%~dp0
if not "%JAVA_HOME%"=="" (
  set JAVA_CMD=%JAVA_HOME%\bin\java.exe
) else (
  set JAVA_CMD=java.exe
)
"%JAVA_CMD%" -cp "%DIR%\gradle\wrapper\gradle-wrapper.jar" org.gradle.wrapper.GradleWrapperMain %*
