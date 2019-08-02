/*
 * This D-script prints one line on each Java class
 * load and unload. To mark DTrace message begin, this
 * script prints a message on DTrace session start.
 */

BEGIN {
    printf("dtrace start\n");
}

hotspot$1:::class-loaded {
    printf("loaded %s\n", copyinstr(arg0, arg1));
}

hotspot$1:::class-unloaded {
    printf("unloaded %s\n", copyinstr(arg0, arg1));
}
