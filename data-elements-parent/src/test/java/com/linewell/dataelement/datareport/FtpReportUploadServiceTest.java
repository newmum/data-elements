package com.linewell.dataelement.datareport;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.linewell.dataelement.dataassets.runtime.DataSourceConnectionPropertyResolver;
import org.apache.commons.net.ftp.FTPClient;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.jdbc.core.JdbcTemplate;

class FtpReportUploadServiceTest {

    @Test
    void createsMissingFtpLevelsWithRelativeMkdCommands() throws Exception {
        FTPClient client = mock(FTPClient.class);
        when(client.changeWorkingDirectory("/")).thenReturn(true);
        when(client.changeWorkingDirectory("FJGHCC")).thenReturn(true);
        when(client.changeWorkingDirectory("tenant-1")).thenReturn(false, true);
        when(client.changeWorkingDirectory("restaurant_tab")).thenReturn(false, true);
        when(client.makeDirectory("tenant-1")).thenReturn(true);
        when(client.makeDirectory("restaurant_tab")).thenReturn(true);

        service().ensureFtpDirectory("transfer-1", client, "/FJGHCC/tenant-1/restaurant_tab");

        InOrder calls = inOrder(client);
        calls.verify(client).changeWorkingDirectory("/");
        calls.verify(client).changeWorkingDirectory("FJGHCC");
        calls.verify(client).changeWorkingDirectory("tenant-1");
        calls.verify(client).makeDirectory("tenant-1");
        calls.verify(client).changeWorkingDirectory("tenant-1");
        calls.verify(client).changeWorkingDirectory("restaurant_tab");
        calls.verify(client).makeDirectory("restaurant_tab");
        calls.verify(client).changeWorkingDirectory("restaurant_tab");
        verify(client, never()).makeDirectory(startsWith("/"));
    }

    @Test
    void acceptsDirectoryCreatedByAnotherUploadBetweenMkdAndCwd() throws Exception {
        FTPClient client = mock(FTPClient.class);
        when(client.changeWorkingDirectory("/")).thenReturn(true);
        when(client.changeWorkingDirectory("FJGHCC")).thenReturn(false, true);
        when(client.makeDirectory("FJGHCC")).thenReturn(false);

        assertDoesNotThrow(() -> service().ensureFtpDirectory("transfer-2", client, "/FJGHCC"));
        verify(client).makeDirectory("FJGHCC");
        verify(client, never()).makeDirectory(startsWith("/"));
    }

    private FtpReportUploadService service() {
        return new FtpReportUploadService(
                mock(JdbcTemplate.class),
                mock(DataSourceConnectionPropertyResolver.class)
        );
    }
}
