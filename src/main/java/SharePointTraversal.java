import com.azure.identity.DeviceCodeCredential;
import com.azure.identity.DeviceCodeCredentialBuilder;
import com.azure.identity.UsernamePasswordCredential;
import com.azure.identity.UsernamePasswordCredentialBuilder;
import com.microsoft.graph.authentication.TokenCredentialAuthProvider;
import com.microsoft.graph.models.DriveItem;
import com.microsoft.graph.requests.GraphServiceClient;
import okhttp3.Request;

import java.util.List;

public class SharePointTraversal {

    private static final String CLIENT_ID = "d3590ed6-52b3-4102-aeff-aad2292ab01c"; // Public client ID provided by Microsoft
    private static final String TENANT_ID = "172f4752-6874-4876-bad5-e6d61f991171"; // Replace with your actual tenant ID
    private static final String SITE_ID = "ebrd0.sharepoint.com,e2ada248-c67d-49af-9f00-489ff62d0103,2cb48d6b-2a26-4592-ac40-5f05e5758a69"; // Replace with your actual site ID
    private static final String DOCUMENT_LIBRARY_ID = "b!SKKt4n3Gr0mfAEif9i0BA2uNtCwmKpJFrEBfBeV1immyu_113jstQrIvoR0ENrqE"; // Replace with your actual document library ID
    private static final String FOLDER_PATH = "/General/COUNTRY"; // Replace with the folder path you want to start from


    public static void main(String[] args) {
        try {
            // Authenticate using Device Code Flow
            DeviceCodeCredential deviceCodeCredential = new DeviceCodeCredentialBuilder()
                    .clientId(CLIENT_ID)
                    .tenantId(TENANT_ID)
                    .challengeConsumer(challenge -> System.out.println(challenge.getMessage()))
                    .build();

            List<String> scopes = List.of("https://graph.microsoft.com/.default");
            TokenCredentialAuthProvider authProvider = new TokenCredentialAuthProvider(scopes, deviceCodeCredential);

            // Initialize Graph client
            GraphServiceClient<Request> graphClient = GraphServiceClient.builder()
                    .authenticationProvider(authProvider)
                    .buildClient();

            // Traverse document library
//            traverseDocumentLibrary(graphClient, DOCUMENT_LIBRARY_ID);


            // Get folder ID by path
            String folderId = getFolderIdByPath(graphClient, DOCUMENT_LIBRARY_ID, FOLDER_PATH);

            // Traverse document library from the specified folder
            if (folderId != null) {
                traverseFolder(graphClient, DOCUMENT_LIBRARY_ID, folderId,FOLDER_PATH);
            } else {
                System.err.println("Folder not found: " + FOLDER_PATH);
            }
        } catch (Exception e) {
            System.err.println("Authentication failed: " + e.getMessage());
            e.printStackTrace();
        }
    }

//    private static void traverseDocumentLibrary(GraphServiceClient<Request> graphClient, String documentLibraryId) {
//        // Get root items of the document library
//        List<DriveItem> rootItems = graphClient.sites(SITE_ID)
//                .drives(documentLibraryId)
//                .root()
//                .children()
//                .buildRequest()
//                .get()
//                .getCurrentPage();
//
//        // Traverse each item
//        for (DriveItem item : rootItems) {
//            traverseItem(graphClient, documentLibraryId, item);
//        }
//    }

    private static void traverseItem(GraphServiceClient<Request> graphClient, String documentLibraryId, DriveItem item, String currentPath) {
        String itemPath = currentPath + "/" + item.name;

        // If item is a file, print its name and path
        if (item.file != null) {
            System.out.println("File: " + item.name + " Path: " + itemPath);
        }

        // If item is a folder, recursively traverse its children
        if (item.folder != null) {
            List<DriveItem> children = graphClient.sites(SITE_ID)
                    .drives(documentLibraryId)
                    .items(item.id)
                    .children()
                    .buildRequest()
                    .get()
                    .getCurrentPage();

            for (DriveItem child : children) {
                traverseItem(graphClient, documentLibraryId, child, itemPath);
            }
        }
    }
    private static String getFolderIdByPath(GraphServiceClient<Request> graphClient, String documentLibraryId, String folderPath) {
        try {
            DriveItem folder = graphClient.sites(SITE_ID)
                    .drives(documentLibraryId)
                    .root()
                    .itemWithPath(folderPath)
                    .buildRequest()
                    .get();
            return folder.id;
        } catch (Exception e) {
            System.err.println("Failed to get folder ID by path: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }
    private static void traverseFolder(GraphServiceClient<Request> graphClient, String documentLibraryId, String folderId, String currentPath) {
        // Get items of the specified folder
        List<DriveItem> items = graphClient.sites(SITE_ID)
                .drives(documentLibraryId)
                .items(folderId)
                .children()
                .buildRequest()
                .get()
                .getCurrentPage();

        // Traverse each item
        for (DriveItem item : items) {
            traverseItem(graphClient, documentLibraryId, item, currentPath);
        }
    }
}
