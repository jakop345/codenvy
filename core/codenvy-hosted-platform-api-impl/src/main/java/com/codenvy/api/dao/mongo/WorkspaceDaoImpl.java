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
package com.codenvy.api.dao.mongo;

import com.mongodb.MongoException;
import com.mongodb.MongoWriteException;
import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.IndexOptions;

import org.bson.Document;
import org.eclipse.che.api.core.ConflictException;
import org.eclipse.che.api.core.NotFoundException;
import org.eclipse.che.api.core.ServerException;
import org.eclipse.che.api.workspace.server.model.impl.UsersWorkspaceImpl;
import org.eclipse.che.api.workspace.server.spi.WorkspaceDao;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import java.util.ArrayList;
import java.util.List;

import static com.codenvy.api.dao.mongo.MongoUtil.handleWriteConflict;
import static com.mongodb.client.model.Filters.and;
import static com.mongodb.client.model.Filters.eq;
import static java.lang.String.format;
import static java.util.Objects.requireNonNull;

/**
 * Implementation of {@link WorkspaceDao} based on MongoDB storage.
 *
 * <p>Workspace collection document scheme:
 * <pre>
 *
 * {
 *      "id" : "workspace123",
 *      "config" : {
 *          "name" : "my-workspace",
 *          "description" : "This is workspace description",
 *          "defaultEnv" : "dev-env",
 *          "commands" : [
 *              {
 *                  "name" : "mci",
 *                  "commandLine" : "maven clean install",
 *                  "type" : "maven",
 *                  "attributes" : [
 *                      {
 *                          "name" : "attribute1",
 *                          "value" : "value1"
 *                      }
 *                   ]
 *              }
 *          ],
 *          "projects" : [
 *              {
 *                  "name" : "my-project",
 *                  "path" : "/path/to/project",
 *                  "description" : "This is project description",
 *                  "type" : "project-type",
 *                  "source" : {
 *                      "type" : "storage-type",
 *                      "location" : "storage-location",
 *                      "parameters" : [
 *                          {
 *                              "name" : "parameter1",
 *                              "value" : "parameter-value"
 *                          }
 *                      ]
 *                  },
 *                  "modules": [
 *                      {
 *                          "name":"my-module",
 *                          "path":"/path/to/project/my-module",
 *                          "type":"maven",
 *                          "mixins" : [ "mixinType1", "mixinType2" ],
 *                          "description" : "This is module description",
 *                          "attributes" : [
 *                              {
 *                                  "name" : "module-attribute-1",
 *                                  "value" : [ "value1", "value2" ]
 *                              }
 *                          ]
 *                      }
 *                  ],
 *                  "mixins" : [ "mixinType1", "mixinType2" ],
 *                  "attributes" : [
 *                      {
 *                          "name" : "project-attribute-1",
 *                          "value" : [ "value1", "value2" ]
 *                      }
 *                  ]
 *              }
 *          ],
 *          "environments" : [
 *              {
 *                  "name" : "dev-env",
 *                  "recipe" : {
 *                      "type" : "dockerfile",
 *                      "script" : "FROM codenvy/jdk7\nCMD tail -f /dev/null"
 *                  },
 *                  "machineConfigs" : [
 *                      {
 *                          "isDev" : true,
 *                          "name" : "dev",
 *                          "type" : "machine-type",
 *                          "limits" : {
 *                              "ram" : 512
 *                          },
 *                          "source" : {
 *                              "type" : "recipe",
 *                              "location" : "recipe-url"
 *                          }
 *                      }
 *                  ]
 *              }
 *          ],
 *          "attributes" : [
 *              {
 *                  "name" : "attribute1",
 *                  "value" : "value1"
 *              }
 *          ]
 *      },
 *      "owner" : "user123",
 *      "servers" : [
 *          {
 *              "ref" : "some_reference",
 *              "port" : "9090/udp",
 *              "protocol" : "some_protocol",
 *              "path" : "/some/path"
 *          }
 *      ],
 *      envVariables : [
 *          {
 *              "name" : "var_name1",
 *              "value" : "var_value1"
 *          }
 *      ]
 * }
 * </pre>
 *
 * <p>Note that for string maps - list of objects used instead of single object,
 * actually the reason is that MongoDB does not support keys which contain DOT<i>.</i>
 * <pre>
 *     //map as single object
 *     {
 *         "attribute1" : "value1",
 *         "attribute2" : "value2"
 *     }
 *
 *     //map as list of objects
 *     [
 *          {
 *              "name" : "attribute1",
 *              "value" : "value1"
 *          },
 *          {
 *              "name" : "attribute2",
 *              "value" : "value2"
 *          }
 *     ]
 * </pre>
 *
 * @author Yevhenii Voevodin
 */
@Singleton
public class WorkspaceDaoImpl implements WorkspaceDao {

    private final MongoCollection<UsersWorkspaceImpl> collection;

    @Inject
    public WorkspaceDaoImpl(@Named("mongo.db.organization") MongoDatabase database,
                            @Named("organization.storage.db.workspace2.collection") String collectionName) {
        collection = database.getCollection(collectionName, UsersWorkspaceImpl.class);
        collection.createIndex(new Document("config.name", 1).append("owner", 1), new IndexOptions().unique(true));
    }

    @Override
    public UsersWorkspaceImpl create(UsersWorkspaceImpl workspace) throws ConflictException, ServerException {
        requireNonNull(workspace, "Workspace must not be null");
        requireNonNull(workspace.getConfig(), "Workspace config must not be null");
        try {
            collection.insertOne(workspace);
        } catch (MongoWriteException writeEx) {
            handleWriteConflict(writeEx, format("Workspace with id '%s' or combination of name '%s' & owner '%s' already exists",
                                                workspace.getId(),
                                                workspace.getConfig().getName(),
                                                workspace.getOwner()));
        } catch (MongoException mongoEx) {
            throw new ServerException(mongoEx.getMessage(), mongoEx);
        }
        return workspace;
    }

    @Override
    public UsersWorkspaceImpl update(UsersWorkspaceImpl update) throws NotFoundException, ConflictException, ServerException {
        requireNonNull(update, "Workspace update must not be null");
        requireNonNull(update.getConfig(), "Workspace update config must not be null");
        try {
            if (collection.findOneAndReplace(eq("_id", update.getId()), update) == null) {
                throw new NotFoundException("Workspace with id '" + update.getId() + "' was not found");
            }
        } catch (MongoWriteException writeEx) {
            handleWriteConflict(writeEx, format("Workspace with id '%s' or combination of name '%s' & owner '%s' already exists",
                                                update.getId(),
                                                update.getConfig().getName(),
                                                update.getOwner()));
        } catch (MongoException mongoEx) {
            throw new ServerException(mongoEx.getMessage(), mongoEx);
        }
        return update;
    }

    @Override
    public void remove(String id) throws ConflictException, ServerException {
        requireNonNull(id, "Workspace identifier must not be null");

        collection.findOneAndDelete(eq("_id", id));
    }

    @Override
    public UsersWorkspaceImpl get(String id) throws NotFoundException, ServerException {
        requireNonNull(id, "Workspace identifier must not be null");

        final FindIterable<UsersWorkspaceImpl> findIt = collection.find(eq("_id", id));
        if (findIt.first() == null) {
            throw new NotFoundException("Workspace with id '" + id + "' was not found");
        }
        return findIt.first();
    }

    @Override
    public UsersWorkspaceImpl get(String name, String owner) throws NotFoundException, ServerException {
        requireNonNull(name, "Workspace name must not be null");
        requireNonNull(owner, "Workspace owner must not be null");

        final FindIterable<UsersWorkspaceImpl> findIt = collection.find(and(eq("config.name", name), eq("owner", owner)));
        if (findIt.first() == null) {
            throw new NotFoundException(format("Workspace with name '%s' and owner '%s' was not found", name, owner));
        }
        return findIt.first();
    }

    @Override
    public List<UsersWorkspaceImpl> getByOwner(String owner) throws ServerException {
        requireNonNull(owner, "Workspace owner must not be null");

        return collection.find(eq("owner", owner)).into(new ArrayList<>());
    }

}