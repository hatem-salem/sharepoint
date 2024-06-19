import com.azure.identity.DeviceCodeCredential;
import com.azure.identity.DeviceCodeCredentialBuilder;
import com.azure.identity.UsernamePasswordCredential;
import com.azure.identity.UsernamePasswordCredentialBuilder;
import com.microsoft.graph.authentication.TokenCredentialAuthProvider;
import com.microsoft.graph.models.Drive;
import com.microsoft.graph.models.Site;
import com.microsoft.graph.requests.GraphServiceClient;
import okhttp3.Request;

import java.util.List;

public class SharePointIDs {

    // Azure AD application details
    private static final String CLIENT_ID = "d3590ed6-52b3-4102-aeff-aad2292ab01c"; // Public client ID provided by Microsoft
    private static final String TENANT_ID = "172f4752-6874-4876-bad5-e6d61f991171"; // Replace with your actual tenant ID

    // SharePoint site details
    private static final String HOSTNAME = "ebrd0.sharepoint.com"; // Replace with your SharePoint hostname
    private static final String SITE_PATH = "/sites/msteams_e632be"; // Replace with your site path

    public static void main(String[] args) {
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

        // Get site ID
        Site site = graphClient.sites(HOSTNAME + ":" + SITE_PATH)
                .buildRequest()
                .get();
        String siteId = site.id;
        System.out.println("Site ID: " + siteId);

        // Get document library ID
        List<Drive> drives = graphClient.sites(siteId)
                .drives()
                .buildRequest()
                .get()
                .getCurrentPage();

        for (Drive drive : drives) {
            System.out.println("Drive Name: " + drive.name + ", Drive ID: " + drive.id);
        }
    }
}
