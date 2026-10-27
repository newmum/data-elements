package com.linewell.dataelement.metautil.structured;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.util.Map;
import java.util.Objects;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;
import org.apache.commons.net.ftp.FTPSClient;

/** Shares the explicit/implicit and certificate contract with the NiFi FTPS source. */
public final class FtpsClientFactory {
    private FtpsClientFactory() { }
    public static FTPSClient create(Map<String,Object> source,int port) {
        String mode=Objects.toString(source.get("ftpsImplicit"),"auto");
        FTPSClient client=new FTPSClient("TLS","auto".equals(mode)?port==990:Boolean.parseBoolean(mode));
        client.setEndpointCheckingEnabled(true);
        try {
            var trust=TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            KeyStore store=null;
            String path=Objects.toString(source.get("trustStorePath"),"");
            if(!path.isBlank()) {
                store=KeyStore.getInstance(KeyStore.getDefaultType());
                try(var input=Files.newInputStream(Path.of(path))) {
                    store.load(input,Objects.toString(source.get("trustStorePassword"),"").toCharArray());
                }
            }
            trust.init(store);
            for(var manager:trust.getTrustManagers()) if(manager instanceof X509TrustManager x509) {
                client.setTrustManager(x509);break;
            }
            return client;
        } catch(Exception failure){throw new IllegalArgumentException("FTPS 信任库配置无效，请核对服务端证书配置",failure);}
    }
}
