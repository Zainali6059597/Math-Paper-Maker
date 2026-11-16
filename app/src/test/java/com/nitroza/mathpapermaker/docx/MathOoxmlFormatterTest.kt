package com.nitroza.mathpapermaker.docx

import org.junit.Assert.assertTrue
import org.junit.Test

class MathOoxmlFormatterTest {
    @Test fun fractionTemplateBecomesEditableWordFraction() {
        val xml = MathOoxmlFormatter.inlineRuns("(a+b)/(c+d)")
        assertTrue(xml.contains("<m:oMath><m:f>"))
        assertTrue(xml.contains("<m:num>"))
        assertTrue(xml.contains("<m:den>"))
        assertTrue(xml.contains("a+b"))
        assertTrue(xml.contains("c+d"))
    }

    @Test fun squareRootTemplateBecomesWordRadical() {
        val xml = MathOoxmlFormatter.inlineRuns("√(x+1)")
        assertTrue(xml.contains("<m:rad>"))
        assertTrue(xml.contains("<m:degHide"))
        assertTrue(xml.contains("x+1"))
    }

    @Test fun powerAndSubscriptTemplatesBecomeWordStructures() {
        val power = MathOoxmlFormatter.inlineRuns("x^(n+1)")
        val subscript = MathOoxmlFormatter.inlineRuns("a_(2)")
        assertTrue(power.contains("<m:sSup>"))
        assertTrue(power.contains("<m:sup>"))
        assertTrue(subscript.contains("<m:sSub>"))
        assertTrue(subscript.contains("<m:sub>"))
    }

    @Test fun nestedTemplatesRemainNestedAndEditable() {
        val xml = MathOoxmlFormatter.inlineRuns("√((a)/(b))")
        assertTrue(xml.contains("<m:rad>"))
        assertTrue(xml.contains("<m:f>"))
        assertTrue(xml.contains("<m:num>"))
        assertTrue(xml.contains("<m:den>"))
    }

    @Test fun ordinaryTextIsKeptEditableAndXmlEscaped() {
        val xml = MathOoxmlFormatter.inlineRuns("Find x < 5 & y > 2")
        assertTrue(xml.contains("<w:r>"))
        assertTrue(xml.contains("x &lt; 5 &amp; y &gt; 2"))
    }
}
