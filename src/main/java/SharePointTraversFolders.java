import com.azure.identity.DeviceCodeCredential;
import com.azure.identity.DeviceCodeCredentialBuilder;
import com.microsoft.graph.authentication.TokenCredentialAuthProvider;
import com.microsoft.graph.models.DriveItem;
import com.microsoft.graph.requests.DriveItemCollectionPage;
import com.microsoft.graph.requests.GraphServiceClient;
import okhttp3.Request;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

public class SharePointTraversFolders {

    private static final String CLIENT_ID = "d3590ed6-52b3-4102-aeff-aad2292ab01c"; // Public client ID provided by Microsoft
    private static final String TENANT_ID = "172f4752-6874-4876-bad5-e6d61f991171"; // Replace with your actual tenant ID
    private static final String SITE_ID = "ebrd0.sharepoint.com,e2ada248-c67d-49af-9f00-489ff62d0103,2cb48d6b-2a26-4592-ac40-5f05e5758a69"; // Replace with your actual site ID
    private static final String DOCUMENT_LIBRARY_ID = "b!SKKt4n3Gr0mfAEif9i0BA2uNtCwmKpJFrEBfBeV1immyu_113jstQrIvoR0ENrqE"; // Replace with your actual document library ID
    private static final String FOLDER_PATH = "/General/COUNTRY"; // Replace with the folder path you want to analyze
    private static final ExecutorService executorService = Executors.newFixedThreadPool(6);
    private static final AtomicLong folderCount = new AtomicLong(0); // Static folder counter
    private static PrintWriter csvWriter;

    public static void main(String[] args) {
        try {
            csvWriter = new PrintWriter(new FileWriter("d:\\folders.csv"));
            csvWriter.println("Folder Path");

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

            // Get folder ID by path
            String folderId = getFolderIdByPath(graphClient, DOCUMENT_LIBRARY_ID, FOLDER_PATH);

            if (folderId != null) {
                AtomicLong fileCount = new AtomicLong(0);
                AtomicLong totalSize = new AtomicLong(0);
                CountDownLatch latch = new CountDownLatch(1);

                // Traverse folder and accumulate metrics
                executorService.submit(() -> {
                    try {
                        traverseFolder(graphClient, DOCUMENT_LIBRARY_ID, folderId, fileCount, totalSize, "");
                    } finally {
                        latch.countDown();
                    }
                });

                // Wait for all tasks to complete
                latch.await();

                // Print results
                System.out.println("Total number of files: " + fileCount.get());
                System.out.println("Total size of files: " + totalSize.get() + " bytes");
                System.out.println("Total number of folders: " + folderCount.get());

                // Close the CSV writer
                csvWriter.close();

                // Shutdown the executor service
                executorService.shutdown();
                executorService.awaitTermination(1, TimeUnit.MINUTES);
            } else {
                System.err.println("Folder not found: " + FOLDER_PATH);
            }
        } catch (Exception e) {
            System.err.println("Authentication failed: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void traverseFolder(GraphServiceClient<Request> graphClient, String documentLibraryId, String folderId, AtomicLong fileCount, AtomicLong totalSize, String currentPath) {
        DriveItemCollectionPage currentPage = graphClient.sites(SITE_ID)
                .drives(documentLibraryId)
                .items(folderId)
                .children()
                .buildRequest()
                .get();

        while (currentPage != null) {
            CountDownLatch latch = new CountDownLatch(currentPage.getCurrentPage().size());
            for (DriveItem item : currentPage.getCurrentPage()) {
                if (item.file != null) {
                    fileCount.incrementAndGet();
                    totalSize.addAndGet(item.size);
                    latch.countDown();
                } else if (item.folder != null) {
                    folderCount.incrementAndGet();
                    String newPath = currentPath + "/" + item.name;
                    writeFolderPathToCSV(newPath);
                    System.out.println(newPath);

                    executorService.submit(() -> {
                        try {
                            traverseNestedFolder(graphClient, documentLibraryId, item.id, fileCount, totalSize, newPath);
                        } finally {
                            latch.countDown();
                        }
                    });
                }
            }
            try {
                latch.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            currentPage = currentPage.getNextPage() != null ? currentPage.getNextPage().buildRequest().get() : null;
        }
        System.out.println("In progress number of folders: " + folderCount.get());
    }

    private static void traverseNestedFolder(GraphServiceClient<Request> graphClient, String documentLibraryId, String folderId, AtomicLong fileCount, AtomicLong totalSize, String currentPath) {
        DriveItemCollectionPage currentPage = graphClient.sites(SITE_ID)
                .drives(documentLibraryId)
                .items(folderId)
                .children()
                .buildRequest()
                .get();

        while (currentPage != null) {
            for (DriveItem item : currentPage.getCurrentPage()) {
                if (item.file != null) {
                    fileCount.incrementAndGet();
                    totalSize.addAndGet(item.size);
                } else if (item.folder != null) {
                    folderCount.incrementAndGet();
                    String newPath = currentPath + "/" + item.name;
                    writeFolderPathToCSV(newPath);
                    System.out.println(newPath);
                    traverseNestedFolder(graphClient, documentLibraryId, item.id, fileCount, totalSize, newPath);
                }
            }
            currentPage = currentPage.getNextPage() != null ? currentPage.getNextPage().buildRequest().get() : null;
        }

        // Print the in-progress folder count
        System.out.println("In progress number of folders: " + folderCount.get());
    }

    private static void writeFolderPathToCSV(String folderPath) {
        csvWriter.println(folderPath);
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
}
