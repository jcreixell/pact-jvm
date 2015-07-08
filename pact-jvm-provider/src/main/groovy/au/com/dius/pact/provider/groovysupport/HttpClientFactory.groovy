package au.com.dius.pact.provider.groovysupport

import org.apache.http.config.Registry
import org.apache.http.config.RegistryBuilder
import org.apache.http.conn.socket.ConnectionSocketFactory
import org.apache.http.conn.socket.PlainConnectionSocketFactory
import org.apache.http.conn.ssl.NoopHostnameVerifier
import org.apache.http.conn.ssl.SSLConnectionSocketFactory
import org.apache.http.impl.client.CloseableHttpClient
import org.apache.http.impl.client.HttpClientBuilder
import org.apache.http.impl.client.HttpClients
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager
import org.apache.http.ssl.SSLContextBuilder

import javax.net.ssl.HostnameVerifier
import javax.net.ssl.SSLContext
import java.security.cert.X509Certificate

class HttpClientFactory {

    public CloseableHttpClient newClient(def provider) {
        if (provider?.createClient != null) {
            if (provider.createClient instanceof Closure) {
                provider.createClient(provider)
            } else {
                Binding binding = new Binding()
                binding.setVariable("provider", provider)
                GroovyShell shell = new GroovyShell(binding)
                shell.evaluate(provider.createClient as String)
            }
        } else if (provider?.insecure) {
            createInsecure()
        } else {
            HttpClients.createDefault()
        }
    }

    private static CloseableHttpClient createInsecure() {
        HttpClientBuilder b = HttpClientBuilder.create()

        // setup a Trust Strategy that allows all certificates.
        //
        SSLContext sslContext = new SSLContextBuilder().loadTrustMaterial(null, { X509Certificate[] chain, String authType ->
            return true
        }).build()
        b.setSslcontext(sslContext)
        // don't check Hostnames, either.
        //      -- use SSLConnectionSocketFactory.getDefaultHostnameVerifier(), if you don't want to weaken
        HostnameVerifier hostnameVerifier = NoopHostnameVerifier.INSTANCE

        // here's the special part:
        //      -- need to create an SSL Socket Factory, to use our weakened "trust strategy";
        //      -- and create a Registry, to register it.
        //
        SSLConnectionSocketFactory sslSocketFactory = new SSLConnectionSocketFactory(sslContext, hostnameVerifier)
        Registry<ConnectionSocketFactory> socketFactoryRegistry = RegistryBuilder.<ConnectionSocketFactory> create()
                .register("http", PlainConnectionSocketFactory.getSocketFactory())
                .register("https", sslSocketFactory)
                .build()

        // now, we create connection-manager using our Registry.
        //      -- allows multi-threaded use
        PoolingHttpClientConnectionManager connMgr = new PoolingHttpClientConnectionManager(socketFactoryRegistry)
        b.setConnectionManager(connMgr)

        // finally, build the HttpClient;
        //      -- done!
        return b.build()
    }
}