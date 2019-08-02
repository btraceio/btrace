syscall:::entry 
/ pid == $target /
{
   @[probefunc] = count();
}
