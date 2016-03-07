/*
 *  [2012] - [2016] Codenvy, S.A.
 *  All Rights Reserved.
 *
 * NOTICE:  All information contained herein is, and remains
 * the property of Codenvy S.A. and its suppliers,
 * if any.  The intellectual and technical concepts contained
 * herein are proprietary to Codenvy S.A.
 * and its suppliers and may be covered by U.S. and Foreign Patents,
 * patents in process, and are protected by trade secret or copyright law.
 * Dissemination of this information or reproduction of this material
 * is strictly forbidden unless prior written permission is obtained
 * from Codenvy S.A..
 */
package org.eclipse.che.ide.ext.microsoft.server;

import com.google.api.client.repackaged.com.google.common.annotations.Beta;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;

import java.util.Objects;

import static java.lang.String.format;

/**
 * Defines URL templates for Mirosoft VSTS API api version 1.0.
 *
 * @author Yevhenii Voevodin
 */
@Beta
@Singleton
public class URLTemplates {

    private static final String SCHEMA = "https";
    private static final String HOST   = "visualstudio.com";

    /** Links for {@link #getTeamBaseUrl(String, String)} url. */
    private static final String REPOSITORY                = "/%s/_apis/git/repositories/%s";
    private static final String REPOSITORIES              = "/%s/_apis/git/repositories";
    private static final String PULL_REQUESTS             = "/_apis/git/repositories/%s/pullrequests";
    private static final String PULL_REQUEST              = "/_apis/git/repositories/%s/pullrequests/%s";
    private static final String PROJECT_REPO_PULL_REQUEST = "/%s/_apis/git/repositories/%s/pullrequests/%s";

    /** Links for {@link #getAppVsspsBaseUrl()} url. */
    public static final String PROFILE = "/_apis/profile/profiles/me";

    private static final String PROJECT_HTTP_REMOTE_URL        = "/_git/%s";
    private static final String PROJECT_REPO_HTTP_REMOTE_URL   = "/%s/_git/%s";
    private static final String PROJECT_HTML_PULL_REQUEST      = "/_git/%s/pullrequest/%s";
    private static final String PROJECT_REPO_HTML_PULL_REQUEST = "/%s/_git/%s/pullrequest/%s";

    private final String apiVersion;

    @Inject
    public URLTemplates(@Named("microsoft.vsts.rest.client.api_version") String apiVersion) {
        this.apiVersion = apiVersion;
    }

    public String profileUrl() {
        return getAppVsspsBaseUrl() + PROFILE;
    }

    /**
     * Returns repository url.
     *
     * @param project
     *         team project id or name
     * @param repoName
     *         id or name of the repository
     * @throws IllegalArgumentException
     *         when either {@code project} or {@code repoName} is null or empty
     */
    public String repositoryUrl(String account, String collection, String project, String repoName) {
        Objects.requireNonNull(project, "Project name required");
        Objects.requireNonNull(repoName, "Repository name required");
        return getTeamBaseUrl(account, collection) + format(REPOSITORY, project, repoName) + getApiVersion();
    }

    /**
     * Returns repositories url.
     *
     * @param project
     *         team project id or name
     * @throws IllegalArgumentException
     *         when {@code project} is null or empty
     */
    public String repositoriesUrl(String account, String collection, String project) {
        Objects.requireNonNull(project, "Project required");
        return getTeamBaseUrl(account, collection) + format(REPOSITORIES, project) + getApiVersion();
    }

    /**
     * Returns the url for pull requests.
     *
     * @param repoId
     *         id of the repository
     * @throws IllegalArgumentException
     *         when {@code repository} is null or empty
     */
    public String pullRequestsUrl(String account, String collection, String repoId) {
        Objects.requireNonNull(repoId, "Repository id required");
        return getTeamBaseUrl(account, collection) + format(PULL_REQUESTS, repoId) + getApiVersion();
    }

    /**
     * Returns pull request url.
     *
     * @param repoId
     *         id of the repository
     * @param pullRequest
     *         id of the pull request
     * @throws IllegalArgumentException
     *         when either {@code repository} or {@code pullRequest} is null or empty
     */
    public String pullRequestUrl(String account, String collection, String repoId, String pullRequest) {
        Objects.requireNonNull(repoId, "Repository required");
        Objects.requireNonNull(pullRequest, "Pull request required");
        return getTeamBaseUrl(account, collection) + format(PULL_REQUEST, repoId, pullRequest) + getApiVersion();
    }

    /**
     * Returns pull request url.
     *
     * @param projectName
     *         the name of the project
     * @param repositoryName
     *         the name of the repository
     * @param pullRequestId
     *         the id of the pull request
     */
    public String pullRequestUrl(String account, String collection, String projectName, String repositoryName, String pullRequestId) {
        Objects.requireNonNull(projectName, "Project name required");
        Objects.requireNonNull(repositoryName, "Repository name required");
        Objects.requireNonNull(pullRequestId, "Pull request id required");
        return getTeamBaseUrl(account, collection) + format(PROJECT_REPO_PULL_REQUEST, projectName, repositoryName, pullRequestId) + getApiVersion();
    }

    /**
     * Returns remote url for given repository.
     *
     * @param projectName
     *         the name of the project
     * @param repositoryName
     *         the name of the repository
     */
    public String httpRemoteUrl(String account, String collection, String projectName, String repositoryName) {
        Objects.requireNonNull(projectName, "Project name required");
        Objects.requireNonNull(repositoryName, "Repository name required");
        String remoteUrl;
        if (projectName.equals(repositoryName)) {
            remoteUrl = getTeamBaseUrl(account, collection) + format(PROJECT_HTTP_REMOTE_URL, projectName);
        } else {
            remoteUrl = getTeamBaseUrl(account, collection) + format(PROJECT_REPO_HTTP_REMOTE_URL, projectName, repositoryName);
        }
        return remoteUrl;
    }

    /**
     * Returns pull request html url.
     *
     * @param projectName
     *         the name of the project
     * @param repositoryName
     *         the name of the repository
     * @param pullRequestId
     *         the id of the pull request
     */
    public String pullRequestHtmlUrl(String account, String collection, String projectName, String repositoryName, String pullRequestId) {
        Objects.requireNonNull(projectName, "Project name required");
        Objects.requireNonNull(repositoryName, "Repository name required");
        Objects.requireNonNull(pullRequestId, "Pull request number required");
        String pullRequestUrl;
        if (projectName.equals(repositoryName)) {
            pullRequestUrl = getTeamBaseUrl(account, collection) + format(PROJECT_HTML_PULL_REQUEST, projectName, pullRequestId);
        } else {
            pullRequestUrl = getTeamBaseUrl(account, collection) + format(PROJECT_REPO_HTML_PULL_REQUEST, projectName, repositoryName, pullRequestId);
        }
        return pullRequestUrl;
    }

    private String getTeamBaseUrl(String account, String collection) {
        return SCHEMA + "://" + account + '.' + HOST + '/' + collection;
    }

    private String getAppVsspsBaseUrl() {
        return SCHEMA + "://app.vssps." + HOST;
    }

    private String getApiVersion() {
        return "?api-version=" + apiVersion;
    }
}
