import com.intellij.lang.Language
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.psi.JavaCodeFragmentFactory
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFileFactory
import javax.swing.text.AttributeSet
import javax.swing.text.Document
import javax.swing.text.Element
import javax.swing.text.html.HTML

class References private constructor(private val project: Project, private val code2psi: Map<Element, PsiElement>) {

    private fun codeElementAt(elem: Element, pos: Int): Element? {
        if (isCode(elem)) return elem
        else {
            val idx = elem.getElementIndex(pos)
            return if (idx < 0) null else codeElementAt(elem.getElement(idx), pos)
        }
    }

    fun resolveAt(doc: Document, pos: Int): PsiElement? {
        val elem = codeElementAt(doc.defaultRootElement, pos)
        if (elem == null) {
            log("Click outside <code> elem")///rm
            return null
        }
        val psiPos = pos - elem.startOffset
        debug(elem, psiPos)///
        return code2psi[elem]?.findReferenceAt(psiPos)?.resolve()
    }

    private fun debug(elem: Element, psiPos: Int) {
        fun inspect(psi: PsiElement, mode: String) {
            log("($mode): psi = $psi")
            val ref = psi.findReferenceAt(psiPos)
            log("($mode): ref = $ref, target = ${ref?.resolve()}")
            val t = ref?.resolve()
            log("($mode): file = ${t?.containingFile}, lang = ${t?.language}, valid = ${t?.isValid}")
        }
        val code = elem.document.getText(elem.startOffset, elem.endOffset - elem.startOffset)
        log("code = $code")
//        val root =
//                JavaPsiFacade.getElementFactory(project).createExpressionFromText(code, null)
        val javaPsi = JavaCodeFragmentFactory.getInstance(project)
                .createExpressionCodeFragment(code, null, null, true)
        val psiFile = PsiFileFactory.getInstance(project).createFileFromText(Language.ANY, code)
        val ktFile = PsiFileFactory.getInstance(project).createFileFromText(
                Language.findLanguageByID("kotlin")!!, code)

//        inspect(root, "expr")
        inspect(javaPsi, "javaFrag")
        if (psiFile != null) inspect(psiFile, "psiFile")
        if (ktFile != null) inspect(ktFile, "ktFile")
    }

    companion object {
        fun build(project: Project, doc: Document): References {
            val dict = HashMap<Element, PsiElement>()
            val factory = JavaCodeFragmentFactory.getInstance(project)

            fun traverse(elem: Element) {
                if (isCode(elem)) {
                    val attrs = elem.attributes.getAttribute(HTML.Tag.CODE) as AttributeSet
                    val langValue = attrs.getAttribute(HTML.Attribute.LANG) as String?
                    val lang = Language.findLanguageByID(langValue?.toLowerCase()) ?: Language.ANY
                    val code = elem.document.getText(elem.startOffset, elem.endOffset - elem.startOffset)
                    log("Found code ($lang) : $code")
//                val psi = JavaPsiFacade.getElementFactory(project)
//                        .createExpressionFromText(code, null)
                    val psi = factory.createExpressionCodeFragment(
                            code, null, null, true)
                    dict.put(elem, psi)
                } else {
                    for (i in 0.until(elem.elementCount))
                        traverse(elem.getElement(i))
                }
            }
            traverse(doc.defaultRootElement)
            log("Found ${dict.size} code sections")
            return References(project, dict)
        }

        private fun isCode(elem: Element) = elem.attributes.isDefined(HTML.Tag.CODE)
    }
}
