package cn.medmi.global.javamethodsignaturecopy

import com.intellij.notification.Notification
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.notification.Notifications
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.TextRange
import com.intellij.psi.*
import com.intellij.psi.util.PsiTreeUtil
import java.awt.datatransfer.StringSelection
import java.util.regex.Pattern

/**
 * @Author medmi
 * @Date 2025/5/11 11:40
 */



class HelloAction : AnAction() {
    override fun actionPerformed(event: AnActionEvent) {
        val project = event.project ?: return showError("Project not available",event.project)
        val editor = event.getData(PlatformDataKeys.EDITOR) ?: return showError("No active editor",event.project)
        val psiFile = event.getData(PlatformDataKeys.PSI_FILE) ?: return showError("Not a Java file",event.project)
        val offset = editor.caretModel.offset

        val element = psiFile.findElementAt(offset) ?: return showError("No element at cursor",event.project)
        val psiMethod = PsiTreeUtil.getParentOfType(element, PsiMethod::class.java, false)
            ?: return showError("Cursor is not on a Java method",event.project)

        try {
            val smaliSig = buildSmaliSignature(project, psiMethod)
            CopyPasteManager.getInstance().setContents(StringSelection(smaliSig))
            showNotification("Smali signature copied",
                "Copied to clipboard: $smaliSig",
                project,
                NotificationType.INFORMATION)
        } catch (e: Exception) {
            showError("Failed to generate signature: ${e.message}", project)
        }
    }

    private fun showNotification(title: String, content: String, project: Project?, type: NotificationType) {
        NotificationGroupManager.getInstance()
            .getNotificationGroup("SmaliGeneratorGroup")
            .createNotification(title, content, type)
            .apply {
                isImportant = false  // 设置为非重要通知（不弹出对话框）
                notify(project)     // 关联到当前项目
            }
    }

    private fun showError(message: String, project: Project?) {
        NotificationGroupManager.getInstance()
            .getNotificationGroup("SmaliGeneratorGroup")
            .createNotification("Error", message, NotificationType.ERROR)
            .apply {
                isImportant = true  // 错误保持为重要通知
                notify(project)
            }
    }


    private fun buildSmaliSignature(project: Project, method: PsiMethod): String {
        val containingClass = method.containingClass ?: throw IllegalStateException("Method has no containing class")
        val classSignature = containingClass.qualifiedName?.replace('.', '/') ?: "UnknownClass"

        val params = method.parameterList.parameters.joinToString("") { param ->
            param.type.toSmaliType(project)
        }

        val returnType = method.returnType?.toSmaliType(project) ?: "V"

        return "L$classSignature;->${method.name}($params)$returnType"
    }

    private fun PsiType.toSmaliType(project: Project): String {
        return when {
            this is PsiPrimitiveType -> getPrimitiveSmaliType(project, this)
            this is PsiArrayType -> "[${componentType.toSmaliType(project)}"
            else -> {
                val canonicalText = canonicalText
                    .replace('.', '/')
                    .replace("\$", "\\\$")
                "L$canonicalText;"
            }
        }
    }

    private fun getPrimitiveSmaliType(project: Project, type: PsiType): String {
        val elementFactory = JavaPsiFacade.getElementFactory(project)
        return when (type.canonicalText) {
            elementFactory.createPrimitiveType(PsiKeyword.VOID)?.canonicalText -> "V"
            elementFactory.createPrimitiveType(PsiKeyword.BOOLEAN)?.canonicalText -> "Z"
            elementFactory.createPrimitiveType(PsiKeyword.BYTE)?.canonicalText -> "B"
            elementFactory.createPrimitiveType(PsiKeyword.CHAR)?.canonicalText -> "C"
            elementFactory.createPrimitiveType(PsiKeyword.SHORT)?.canonicalText -> "S"
            elementFactory.createPrimitiveType(PsiKeyword.INT)?.canonicalText -> "I"
            elementFactory.createPrimitiveType(PsiKeyword.LONG)?.canonicalText -> "J"
            elementFactory.createPrimitiveType(PsiKeyword.FLOAT)?.canonicalText -> "F"
            elementFactory.createPrimitiveType(PsiKeyword.DOUBLE)?.canonicalText -> "D"
            else -> throw IllegalArgumentException("Unsupported primitive type: ${type.canonicalText}")
        }
    }


    override fun update(event: AnActionEvent) {
        val psiFile = event.getData(PlatformDataKeys.PSI_FILE)
        event.presentation.isEnabled = psiFile is PsiJavaFile
    }
}