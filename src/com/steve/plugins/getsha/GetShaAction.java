package com.steve.plugins.getsha;

import com.intellij.json.psi.JsonObject;
import com.intellij.json.psi.JsonProperty;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.impl.status.StatusBarUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.codehaus.jettison.json.JSONObject;

public class GetShaAction extends AnAction {

    private static final String ARTIFACTORY_API = "https://repo.intra.eika.no/api";

    @Override
    public void actionPerformed(AnActionEvent event) {
        Project project = event.getProject();
        try {
            ASTNode parentNode = getParentNode(event);
            JsonObject jsonObject = (JsonObject) parentNode.getPsi();
            JsonProperty artifactIdProperty = null, groupIdProperty = null, versionProperty = null, artifactTypeProperty = null, sha256Property = null;
            for (JsonProperty jsonProperty : jsonObject.getPropertyList()) {
                if (jsonProperty.getName().equals("artifact_id")) artifactIdProperty = jsonProperty;
                if (jsonProperty.getName().equals("group_id")) groupIdProperty = jsonProperty;
                if (jsonProperty.getName().equals("version")) versionProperty = jsonProperty;
                if (jsonProperty.getName().equals("artifact_type")) artifactTypeProperty = jsonProperty;
                if (jsonProperty.getName().equals("sha256_checksum")) sha256Property = jsonProperty;
            }
            if (artifactIdProperty == null) throw new Exception("Couldn't find artifact_id property...");
            if (groupIdProperty == null) throw new Exception("Couldn't find group_id property...");
            if (versionProperty == null) throw new Exception("Couldn't find version property...");
            if (artifactTypeProperty == null) throw new Exception("Couldn't find artifact_type property...");
            if (sha256Property == null) throw new Exception("Couldn't find sha256_checksum property...");

            String artifactId = artifactIdProperty.getValue().getText().replace("\"", "");
            String groupId = groupIdProperty.getValue().getText().replace("\"", "");
            String version = versionProperty.getValue().getText().replace("\"", "");
            String artifactType = artifactTypeProperty.getValue().getText().replace("\"", "");

            StatusBarUtil.setStatusBarInfo(project, "GetSha: looking for " + artifactId + "-" + version + "." + artifactType + " in artifactory...");

            // artifactory api POST - this calculates the sha256
            String repo = version.contains("SNAPSHOT") ? "libs-snapshot-local" : "libs-release-local";
            String artifactPath = groupId.replace(".", "/");
            String calcShaEndpoint = ARTIFACTORY_API + "/checksum/sha256";
            String calcShaPayload = "{\"repoKey\":\"" + repo + " \",\"path\":\"" + artifactPath + "/" + artifactId + "/" + version + "/\"}";


            // artifactory api GET - find the sha256 value
            String getShaEndpoint = ARTIFACTORY_API + "/storage/" + repo + "/" + artifactPath + "/" + artifactId + "/" + version + "/" + artifactId + "-" + version + "." + artifactType;

        } catch (Exception e) {
            StatusBarUtil.setStatusBarInfo(project, "GetSha failed: " + e.getMessage());
        }
    }

    private ASTNode getParentNode(AnActionEvent e) {
        Editor editor = e.getData(PlatformDataKeys.EDITOR);
        PsiFile psiFile = e.getData(LangDataKeys.PSI_FILE);
        if (psiFile == null || editor == null)
            return null;

        int offset = editor.getCaretModel().getOffset();
        PsiElement elementAt = psiFile.findElementAt(offset);
        if (elementAt == null || elementAt.getParent() == null || elementAt.getParent().getNode() == null)
            return null;

        return elementAt.getParent().getNode();
    }
}
