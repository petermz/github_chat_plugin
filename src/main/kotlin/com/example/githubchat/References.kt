package com.example.githubchat

import com.intellij.lang.Language
import com.intellij.lang.java.JavaLanguage
import com.intellij.openapi.project.Project
import com.intellij.psi.JavaCodeFragmentFactory
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFileFactory
import javax.swing.text.AttributeSet
import javax.swing.text.Document
import javax.swing.text.Element
import javax.swing.text.html.HTML

class References private constructor(private val resolvers: Map<Element, Resolver>) {

    private fun codeElementAt(elem: Element, pos: Int): Element? {
        if (isCode(elem)) return elem
        else {
            val idx = elem.getElementIndex(pos)
            return if (idx < 0) null else codeElementAt(elem.getElement(idx), pos)
        }
    }

    fun resolveAt(doc: Document, pos: Int): PsiElement? {
        val elem = codeElementAt(doc.defaultRootElement, pos) ?: return null
        val psiPos = pos - elem.startOffset
        return resolvers[elem]?.resolve(psiPos)
    }

    companion object {
        fun build(project: Project, doc: Document): References {
            val dict = HashMap<Element, Resolver>()
            val fragFactory = JavaCodeFragmentFactory.getInstance(project)
            val fileFactory = PsiFileFactory.getInstance(project)

            fun traverse(elem: Element) {
                if (isCode(elem)) {
                    val attrs = elem.attributes.getAttribute(HTML.Tag.CODE) as AttributeSet
                    val langValue = attrs.getAttribute(HTML.Attribute.LANG) as String?
                    val lang = Language.findLanguageByID(langValue) ?: JavaLanguage.INSTANCE
                    val code = elem.document.getText(elem.startOffset, elem.endOffset - elem.startOffset)
                    log("Found code ($lang) : $code")
                    val resolvers = listOf(
                            fragFactory.createExpressionCodeFragment(
                                    code, null, null, true),
                            fileFactory.createFileFromText(lang, code)
                    ).filterNotNull()
                    dict.put(elem, Resolver(resolvers))
                } else {
                    for (i in 0.until(elem.elementCount))
                        traverse(elem.getElement(i))
                }
            }
            traverse(doc.defaultRootElement)
            return References(dict)
        }

        private fun isCode(elem: Element) = elem.attributes.isDefined(HTML.Tag.CODE)
    }
}

// fallback-capable PsiReference resolver
private class Resolver(private val resolvers: List<PsiElement>) {
    fun resolve(pos: Int): PsiElement? {
        for (psi in resolvers) {
            val ref = psi.findReferenceAt(pos)
            val target = ref?.resolve()
            log("RSLV: ref=$ref (${ref?.element?.text}), t=$target")
            if (target != null) return target
        }
        return null
    }
}