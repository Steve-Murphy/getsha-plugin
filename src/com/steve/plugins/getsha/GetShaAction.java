package com.steve.plugins.getsha;

import com.intellij.json.psi.JsonObject;
import com.intellij.json.psi.JsonProperty;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.impl.status.StatusBarUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Base64;

public class GetShaAction extends AnAction {

    private static final String ARTIFACTORY_API = "https://repo.intra.company.no/api";
    private static final String ARTIFACTORY_USER_PASS = "user:pass";

    @Override
    public void actionPerformed(AnActionEvent event) {
        Project project = event.getProject();
        try {
            ASTNode parentNode = getParentJsonObjectNode(event);
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

            doPost(calcShaEndpoint, calcShaPayload);

            // artifactory api GET - find the sha256 value
            String getShaEndpoint = ARTIFACTORY_API + "/storage/" + repo + "/" + artifactPath + "/" + artifactId + "/" + version + "/" + artifactId + "-" + version + "." + artifactType;

            String getResult = doGet(getShaEndpoint);

            // find the sha256 value from the json response
            final String sha256 = extractJsonValue(getResult, "sha256");

            // update the source code
            final ASTNode node = sha256Property.getNode().getLastChildNode();
            final Editor editor = event.getData(PlatformDataKeys.EDITOR);
            final int startOffset = node.getStartOffset();
            final int endOffset = startOffset + node.getTextLength();

            new WriteCommandAction.Simple(project, "GetSha", node.getPsi().getContainingFile()) {
                @Override
                protected void run() throws Throwable {
                    editor.getDocument().replaceString(startOffset, endOffset, "\"" + sha256 + "\"");
                }
            }.execute();

            StatusBarUtil.setStatusBarInfo(project, "GetSha: success.");

        } catch (Exception e) {
            StatusBarUtil.setStatusBarInfo(project, "GetSha failed: " + e.getMessage());

        }
    }

    private String extractJsonValue(String json, String key) throws Exception {
        String[] keyValues = json.replace(" ", "").split("\n");
        for (String keyValue : keyValues) {
            if (keyValue.startsWith("\"" + key + "\"")) {
                key = keyValue.split(":")[1].replace("\"", "");
                return key;
            }
        }
        throw new Exception("Couldn't find " + key + " value from json result.");
    }

    private void doPost(String endpoint, String payload) throws Exception {
        URL url = new URL(endpoint);
        HttpURLConnection con = (HttpURLConnection) url.openConnection();
        con.setRequestMethod("POST");
        con.setInstanceFollowRedirects(true);
        con.setRequestProperty("Authorization", "Basic " + Base64.getEncoder().encodeToString(ARTIFACTORY_USER_PASS.getBytes()));
        con.setRequestProperty("Content-Type", "application/json");
        con.setRequestProperty("Content-length", String.valueOf(payload.length()));
        con.setDoOutput(true);
        con.setDoInput(true);

        DataOutputStream output = new DataOutputStream(con.getOutputStream());
        output.writeBytes(payload);
        output.close();

        int code = con.getResponseCode();
        if (code != 200) throw new Exception(code + ": " + con.getResponseMessage());

        // read the response
        DataInputStream input = new DataInputStream(con.getInputStream());
        int c;
        StringBuilder resultBuf = new StringBuilder();
        while ( (c = input.read()) != -1) {
            resultBuf.append((char) c);
        }
        input.close();
    }

    private String doGet(String endpoint) throws Exception {
        URL url = new URL(endpoint);
        HttpURLConnection con = (HttpURLConnection) url.openConnection();
        con.setRequestMethod("GET");
        con.setInstanceFollowRedirects(true);
        con.setRequestProperty("Authorization", "Basic " + Base64.getEncoder().encodeToString(ARTIFACTORY_USER_PASS.getBytes()));
//        con.setRequestProperty("Content-Type", "application/json");
//        con.setRequestProperty("Content-length", String.valueOf(payload.length()));
        con.setDoOutput(true);
        con.setDoInput(true);

//        DataOutputStream output = new DataOutputStream(con.getOutputStream());
//        output.writeBytes(payload);
//        output.close();

        int code = con.getResponseCode();
        if (code != 200) throw new Exception(code + ": " + con.getResponseMessage());

        // read the response
        DataInputStream input = new DataInputStream(con.getInputStream());
        int c;
        StringBuilder resultBuf = new StringBuilder();
        while ( (c = input.read()) != -1) {
            resultBuf.append((char) c);
        }
        input.close();

        return resultBuf.toString();
    }

    private ASTNode getParentJsonObjectNode(AnActionEvent e) {
        Editor editor = e.getData(PlatformDataKeys.EDITOR);
        PsiFile psiFile = e.getData(LangDataKeys.PSI_FILE);
        if (psiFile == null || editor == null)
            return null;

        int offset = editor.getCaretModel().getOffset();
        PsiElement elementAt = psiFile.findElementAt(offset);
        if (elementAt == null || elementAt.getParent() == null || elementAt.getParent().getNode() == null)
            return null;

        while (elementAt != null && !elementAt.toString().equals("JsonObject")) {
            elementAt = elementAt.getParent();
        }

        return elementAt.getNode();
    }
}
