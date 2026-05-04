package com.example.sca.repository;

import com.example.sca.model.RestEndpoint;
import org.springframework.data.couchbase.repository.CouchbaseRepository;
import org.springframework.data.couchbase.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Spring Data Couchbase repository for {@link RestEndpoint} documents.
 */
@Repository
public interface RestEndpointRepository
        extends CouchbaseRepository<RestEndpoint, String> {

    /** All endpoints for a project. */
    List<RestEndpoint> findByProjectRoot(String projectRoot);

    /** Endpoints exposed by a specific controller (by qualified name). */
    List<RestEndpoint> findByProjectRootAndControllerQualifiedName(
        String projectRoot, String controllerQualifiedName);

    /** Endpoints exposed by a specific controller (by simple name). */
    List<RestEndpoint> findByProjectRootAndControllerSimpleName(
        String projectRoot, String controllerSimpleName);

    /** Endpoints filtered by HTTP method (GET, POST, PUT, DELETE, PATCH). */
    List<RestEndpoint> findByProjectRootAndHttpMethod(
        String projectRoot, String httpMethod);

    /** Endpoints whose path contains a given fragment. */
    @Query("#{#n1ql.selectEntity} WHERE #{#n1ql.filter} "
         + "AND projectRoot = $1 AND CONTAINS(LOWER(`path`), LOWER($2))")
    List<RestEndpoint> findByProjectRootAndPathContaining(
        String projectRoot, String pathFragment);

    /** Delete all endpoints for a project. */
    void deleteByProjectRoot(String projectRoot);
}
