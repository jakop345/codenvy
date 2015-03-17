/*
 * CODENVY CONFIDENTIAL
 * __________________
 *
 *  [2012] - [2015] Codenvy, S.A.
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
package com.codenvy.auth.sso.server;

import com.codenvy.api.account.server.dao.Account;
import com.codenvy.api.account.server.dao.AccountDao;
import com.codenvy.api.core.NotFoundException;
import com.codenvy.api.core.ServerException;
import com.codenvy.api.dao.authentication.AccessTicket;
import com.codenvy.api.user.server.dao.PreferenceDao;
import com.codenvy.api.user.server.dao.User;
import com.codenvy.api.user.server.dao.UserDao;
import com.codenvy.api.workspace.server.dao.Member;
import com.codenvy.api.workspace.server.dao.MemberDao;
import com.codenvy.commons.user.UserImpl;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.testng.MockitoTestNGListener;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Listeners;
import org.testng.annotations.Test;

import java.util.Collections;
import java.util.HashSet;

import static java.util.Arrays.asList;
import static java.util.Collections.singleton;
import static java.util.Collections.singletonMap;
import static org.mockito.Mockito.*;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

/**
 * Tests for {@link OrgServiceRolesExtractor}
 *
 * @author Eugene Voevodin
 */
@Listeners(MockitoTestNGListener.class)
public class OrgServiceRolesExtractorTest {

    @Mock
    UserDao                  userDao;
    @Mock
    AccountDao               accountDao;
    @Mock
    MemberDao                memberDao;
    @Mock
    PreferenceDao            preferenceDao;
    @InjectMocks
    OrgServiceRolesExtractor extractor;

    AccessTicket ticket;

    @BeforeMethod
    public void setUp() throws Exception {
        final UserImpl user = new UserImpl("name",
                                           "id",
                                           "token",
                                           Collections.<String>emptyList(),
                                           false);

        ticket = new AccessTicket("token", user, "authHandler");

        when(userDao.getById(user.getId())).thenReturn(new User().withId("id"));
    }

    @Test
    public void shouldSkipLdapRoleCheckWhenAllowedRoleIsNull() throws ServerException {
        when(memberDao.getUserRelationships(ticket.getPrincipal().getId())).thenReturn(Collections.<Member>emptyList());

        assertEquals(extractor.extractRoles(ticket, "wsId", "accId"), singleton("user"));
    }

    @Test
    public void shouldReturnEmptySetWhenLdapRolesDoNotContainAllowedRole() throws Exception {
        final OrgServiceRolesExtractor extractor = spy(new OrgServiceRolesExtractor(userDao,
                                                                                    accountDao,
                                                                                    memberDao,
                                                                                    preferenceDao,
                                                                                    null,
                                                                                    null,
                                                                                    "member",
                                                                                    "admin",
                                                                                    null));
        doReturn(Collections.<String>emptySet()).when(extractor).getRoles(ticket.getPrincipal().getId());

        assertTrue(extractor.extractRoles(ticket, "wsId", "accId").isEmpty());
    }

    @Test
    public void shouldReturnNormalUserRolesWhenLdapRolesContainAllowedRole() throws Exception {
        final OrgServiceRolesExtractor extractor = spy(new OrgServiceRolesExtractor(userDao,
                                                                                    accountDao,
                                                                                    memberDao,
                                                                                    preferenceDao,
                                                                                    null,
                                                                                    null,
                                                                                    "member",
                                                                                    "codenvy-user",
                                                                                    null));
        doReturn(singleton("codenvy-user")).when(extractor).getRoles(ticket.getPrincipal().getId());

        assertEquals(extractor.extractRoles(ticket, "wsId", "accId"), singleton("user"));
    }

    @Test
    public void shouldReturnTempUserRoleWhenPreferencesContainTemporaryAttribute() throws Exception {
        when(preferenceDao.getPreferences(ticket.getPrincipal().getId())).thenReturn(singletonMap("temporary", "true"));

        assertEquals(extractor.extractRoles(ticket, "wsId", "accId"), singleton("temp_user"));
    }

    @Test
    public void shouldReturnAccountRolesWithUserRoleWhenUserHasAccessToAccount() throws Exception {
        com.codenvy.api.account.server.dao.Member member = new com.codenvy.api.account.server.dao.Member();
        member.withUserId(ticket.getPrincipal().getId()).withRoles(asList("account/owner", "account/member"));
        when(accountDao.getMembers("accId")).thenReturn(asList(member));

        when(accountDao.getById("accId")).thenReturn(new Account());

        assertEquals(extractor.extractRoles(ticket, "wsId", "accId"), new HashSet<>(asList("user", "account/owner", "account/member")));
    }

    @Test
    public void shouldReturnWorkspaceRolesWithUserRoleWhenUserHasAccessToWorkspace() throws Exception {
        final Member member = new Member().withWorkspaceId("wsId")
                                          .withRoles(asList("workspace/admin", "workspace/developer"));
        when(memberDao.getUserRelationships(ticket.getPrincipal().getId())).thenReturn(asList(member));

        final HashSet<String> expectedRoles = new HashSet<>(asList("user", "workspace/developer", "workspace/admin"));
        assertEquals(extractor.extractRoles(ticket, "wsId", "accId"), expectedRoles);
    }

    @Test
    public void shouldReturnEmptySetWhenUserDoesNotExist() throws Exception {
        when(userDao.getById(ticket.getPrincipal().getId())).thenThrow(new NotFoundException("fake"));

        assertTrue(extractor.extractRoles(ticket, "wsId", "accId").isEmpty());
    }

    @Test(expectedExceptions = RuntimeException.class,
          expectedExceptionsMessageRegExp = "fake")
    public void shouldRethrowServerExceptionAsRuntimeException() throws Exception {
        when(userDao.getById(ticket.getPrincipal().getId())).thenThrow(new ServerException("fake"));

        extractor.extractRoles(ticket, "wsId", "accId");
    }
}