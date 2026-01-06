package me.hash.mediaroulette.utils.startup;

import org.junit.jupiter.api.Test;
import java.io.InputStream;
import java.util.Properties;

public class TestStartup {

    @Test
    public void testBannerOutput() {
        System.out.println("=== TEST BANNER OUTPUT ===");
        printBanner();
        System.out.println("==========================");
    }

    private void printBanner() {
        String version = getVersion();
        String title = "🎲 MediaRoulette";
        
        // Match the logic in StartupManager
        int boxWidth = 43;
        String fullKey = title + " v" + version;
        
        int contentLen = title.length() + version.length() + 2; 
        int visualLen = contentLen - 1; 
        
        int paddingLeft = (boxWidth - visualLen) / 2;
        int paddingRight = boxWidth - visualLen - paddingLeft;
        
        String leftPad = " ".repeat(Math.max(0, paddingLeft));
        String rightPad = " ".repeat(Math.max(0, paddingRight));

        System.out.println();
        System.out.println("  ╔═══════════════════════════════════════════╗");
        System.out.println("  ║" + leftPad + title + " v" + version + rightPad + "║");
        System.out.println("  ╚═══════════════════════════════════════════╝");
        System.out.println();
    }
    
    private String getVersion() {
        try (InputStream is = getClass().getResourceAsStream("/version.properties")) {
            if (is != null) {
                Properties props = new Properties();
                props.load(is);
                return props.getProperty("version", "1.0.0");
            }
        } catch (Exception e) {
            // ignore
        }
        return "1.0.0-fallback";
    }
}
