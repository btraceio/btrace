
/*
 * This D-script maintains a aggregation whenever
 * btrace:::event probe is raised with "java-url-open"
 * as first argument.
 */
btrace$target:::event
/ copyinstr(arg0) == "java-url-open" /
{
    @[copyinstr(arg1)] = count();
}

