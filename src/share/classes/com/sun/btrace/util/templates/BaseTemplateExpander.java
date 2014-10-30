/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.sun.btrace.util.templates;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 *
 * @author jbachorik
 */
abstract public class BaseTemplateExpander implements TemplateExpander {
    private final Set<Template> supportedTemplates = new HashSet<Template>();

    private Template lastTemplate = null;

    public BaseTemplateExpander(Template ... templates) {
        this.supportedTemplates.addAll(Arrays.asList(templates));
        BTraceTemplates.registerTemplates(templates);
    }


    public Result expand(TemplateExpanderVisitor v, Template t) {
        if (lastTemplate == null) {
            if (t == null || !supportedTemplates.contains(t)) {
                return Result.IGNORED;
            }

            recordTemplate(t);
            lastTemplate = t;
            return Result.CLAIMED;
        } else {
            if (lastTemplate.equals(t)) {
                recordTemplate(t);
                return Result.CLAIMED;
            } else {
                Result r = expandTemplate(v, lastTemplate);
                lastTemplate = t;
                return t != null ? r : Result.IGNORED;
            }
        }
    }

    abstract protected void recordTemplate(Template t);
    abstract protected Result expandTemplate(TemplateExpanderVisitor v, Template t);
}
