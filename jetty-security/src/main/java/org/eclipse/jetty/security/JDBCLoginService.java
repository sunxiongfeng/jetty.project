//
// ========================================================================
// Copyright (c) 1995-2020 Mort Bay Consulting Pty Ltd and others.
//
// This program and the accompanying materials are made available under
// the terms of the Eclipse Public License 2.0 which is available at
// https://www.eclipse.org/legal/epl-2.0
//
// This Source Code may also be made available under the following
// Secondary Licenses when the conditions for such availability set
// forth in the Eclipse Public License, v. 2.0 are satisfied:
// the Apache License v2.0 which is available at
// https://www.apache.org/licenses/LICENSE-2.0
//
// SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
// ========================================================================
//

package org.eclipse.jetty.security;

import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import javax.servlet.ServletRequest;

import org.eclipse.jetty.util.Loader;
import org.eclipse.jetty.util.resource.Resource;
import org.eclipse.jetty.util.security.Credential;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * HashMapped User Realm with JDBC as data source.
 * The {@link #login(String, Object, ServletRequest)} method checks the inherited Map for the user. If the user is not
 * found, it will fetch details from the database and populate the inherited
 * Map. It then calls the superclass {@link #login(String, Object, ServletRequest)} method to perform the actual
 * authentication. Periodically (controlled by configuration parameter),
 * internal hashes are cleared. Caching can be disabled by setting cache refresh
 * interval to zero. Uses one database connection that is initialized at
 * startup. Reconnect on failures.
 * <p>
 * An example properties file for configuration is in
 * <code>${jetty.home}/etc/jdbcRealm.properties</code>
 */
public class JDBCLoginService extends AbstractLoginService
{
    private static final Logger LOG = LoggerFactory.getLogger(JDBCLoginService.class);

    protected String _config;
    protected String _jdbcDriver;
    protected String _url;
    protected String _userName;
    protected String _password;
    protected String _userTableKey;
    protected String _userTablePasswordField;
    protected String _roleTableRoleField;
    protected Connection _con;
    protected String _userSql;
    protected String _roleSql;

    /**
     * JDBCKnownUser
     */
    public class JDBCUserPrincipal extends UserPrincipal
    {
        int _userKey;

        public JDBCUserPrincipal(String name, Credential credential, int key)
        {
            super(name, credential);
            _userKey = key;
        }

        public int getUserKey()
        {
            return _userKey;
        }
    }

    public JDBCLoginService()
        throws IOException
    {
    }

    public JDBCLoginService(String name)
        throws IOException
    {
        setName(name);
    }

    public JDBCLoginService(String name, String config)
        throws IOException
    {
        setName(name);
        setConfig(config);
    }

    public JDBCLoginService(String name, IdentityService identityService, String config)
        throws IOException
    {
        setName(name);
        setIdentityService(identityService);
        setConfig(config);
    }

    @Override
    protected void doStart() throws Exception
    {
        Properties properties = new Properties();
        Resource resource = Resource.newResource(_config);
        try (InputStream in = resource.getInputStream())
        {
            properties.load(in);
        }
        _jdbcDriver = properties.getProperty("jdbcdriver");
        _url = properties.getProperty("url");
        _userName = properties.getProperty("username");
        _password = properties.getProperty("password");
        _userTableKey = properties.getProperty("usertablekey");
        _userTablePasswordField = properties.getProperty("usertablepasswordfield");
        _roleTableRoleField = properties.getProperty("roletablerolefield");

        final String userTable = properties.getProperty("usertable");
        final String userTableUserField = properties.getProperty("usertableuserfield");
        final String roleTable = properties.getProperty("roletable");
        final String roleTableKey = properties.getProperty("roletablekey");
        final String userRoleTable = properties.getProperty("userroletable");
        final String userRoleTableUserKey = properties.getProperty("userroletableuserkey");
        final String userRoleTableRoleKey = properties.getProperty("userroletablerolekey");

        if (_jdbcDriver == null || _jdbcDriver.equals("") ||
            _url == null || _url.equals("") ||
            _userName == null || _userName.equals("") ||
            _password == null)
        {
            LOG.warn("UserRealm " + getName() + " has not been properly configured");
        }

        _userSql = "select " + _userTableKey + "," + _userTablePasswordField + " from " + userTable + " where " + userTableUserField + " = ?";
        _roleSql = "select r." + _roleTableRoleField +
            " from " + roleTable + " r, " + userRoleTable +
            " u where u." + userRoleTableUserKey + " = ?" +
            " and r." + roleTableKey + " = u." + userRoleTableRoleKey;

        Loader.loadClass(_jdbcDriver).getDeclaredConstructor().newInstance();
        super.doStart();
    }

    public String getConfig()
    {
        return _config;
    }

    /**
     * Load JDBC connection configuration from properties file.
     *
     * @param config Filename or url of user properties file.
     */
    public void setConfig(String config)
    {
        if (isRunning())
            throw new IllegalStateException("Running");
        _config = config;
    }

    /**
     * (re)Connect to database with parameters setup by loadConfig()
     */
    public void connectDatabase()
    {
        try
        {
            Class.forName(_jdbcDriver);
            _con = DriverManager.getConnection(_url, _userName, _password);
        }
        catch (SQLException e)
        {
            LOG.warn("UserRealm " + getName() + " could not connect to database; will try later", e);
        }
        catch (ClassNotFoundException e)
        {
            LOG.warn("UserRealm " + getName() + " could not connect to database; will try later", e);
        }
    }

    @Override
    public UserPrincipal loadUserInfo(String username)
    {
        try
        {
            if (null == _con)
                connectDatabase();

            if (null == _con)
                throw new SQLException("Can't connect to database");

            try (PreparedStatement stat1 = _con.prepareStatement(_userSql))
            {
                stat1.setObject(1, username);
                try (ResultSet rs1 = stat1.executeQuery())
                {
                    if (rs1.next())
                    {
                        int key = rs1.getInt(_userTableKey);
                        String credentials = rs1.getString(_userTablePasswordField);

                        return new JDBCUserPrincipal(username, Credential.getCredential(credentials), key);
                    }
                }
            }
        }
        catch (SQLException e)
        {
            LOG.warn("UserRealm " + getName() + " could not load user information from database", e);
            closeConnection();
        }

        return null;
    }

    @Override
    public String[] loadRoleInfo(UserPrincipal user)
    {
        JDBCUserPrincipal jdbcUser = (JDBCUserPrincipal)user;

        try
        {
            if (null == _con)
                connectDatabase();

            if (null == _con)
                throw new SQLException("Can't connect to database");

            List<String> roles = new ArrayList<String>();

            try (PreparedStatement stat2 = _con.prepareStatement(_roleSql))
            {
                stat2.setInt(1, jdbcUser.getUserKey());
                try (ResultSet rs2 = stat2.executeQuery())
                {
                    while (rs2.next())
                    {
                        roles.add(rs2.getString(_roleTableRoleField));
                    }
                    return roles.toArray(new String[roles.size()]);
                }
            }
        }
        catch (SQLException e)
        {
            LOG.warn("UserRealm " + getName() + " could not load user information from database", e);
            closeConnection();
        }

        return null;
    }

    @Override
    protected void doStop() throws Exception
    {
        closeConnection();
        super.doStop();
    }

    /**
     * Close an existing connection
     */
    private void closeConnection()
    {
        if (_con != null)
        {
            if (LOG.isDebugEnabled())
                LOG.debug("Closing db connection for JDBCUserRealm");
            try
            {
                _con.close();
            }
            catch (Exception e)
            {
                LOG.trace("IGNORED", e);
            }
        }
        _con = null;
    }
}
