call buildSbt.cmd
timeout 1
copy target\scala-2.12\CondorFlocking-assembly-0.1.jar run
pause