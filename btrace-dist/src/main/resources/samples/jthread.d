#!/usr/sbin/dtrace -s

btrace$1:::event 
/ 
  copyinstr(arg0) == "jthreadstart" &&
  arg1 != NULL
/
{
  printf("From DTrace: Java Thread '%s' started\n", copyinstr(arg1));
}
