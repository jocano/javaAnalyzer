package com.example.sca.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.couchbase.config.AbstractCouchbaseConfiguration;
import org.springframework.data.couchbase.repository.config.EnableCouchbaseRepositories;

/**
 * Wires the Couchbase cluster connection from {@code application.properties}.
 *
 * <p>Collections used:
 * <ul>
 *   <li>{@code components}    — {@code JavaComponent} documents</li>
 *   <li>{@code relationships} — {@code ComponentRelationship} documents</li>
 * </ul>
 *
 * Both collections must exist in the configured bucket/scope before the
 * application is started.  See {@code README.md} for setup instructions.
 */
@Configuration
@EnableCouchbaseRepositories(basePackages = "com.example.sca.repository")
public class CouchbaseConfig extends AbstractCouchbaseConfiguration {

    @Value("${spring.couchbase.connection-string:localhost}")
    private String connectionString;

    @Value("${spring.couchbase.username:Administrator}")
    private String username;

    @Value("${spring.couchbase.password:password}")
    private String password;

    @Value("${spring.data.couchbase.bucket-name:java-analyzer}")
    private String bucketName;

    @Override
    public String getConnectionString() { return connectionString; }

    @Override
    public String getUserName()         { return username; }

    @Override
    public String getPassword()         { return password; }

    @Override
    public String getBucketName()       { return bucketName; }

    /**
     * Returns {@code true} so that Spring Data Couchbase
     * creates the primary index automatically if needed.
     */
    @Override
    protected boolean autoIndexCreation() { return true; }
}
