/*
 * $Id$
 * 
 * Firebird Open Source J2EE Connector - JDBC Driver
 *
 * Distributable under LGPL license.
 * You may obtain a copy of the License at http://www.gnu.org/copyleft/lgpl.html
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * LGPL License for more details.
 *
 * This file was created by members of the firebird development team.
 * All individual contributions remain the Copyright (C) of those
 * individuals.  Contributors to this file are either listed here or
 * can be obtained from a CVS history command.
 *
 * All rights reserved.
 */
package org.firebirdsql.gds.impl;

import org.firebirdsql.gds.impl.argument.*;

import java.io.IOException;
import java.io.OutputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * @author <a href="mailto:mrotteveel@users.sourceforge.net">Mark Rotteveel</a>
 */
public class ParameterBufferBase implements Serializable {

    private final List<Argument> arguments = new ArrayList<Argument>();

    public void addArgument(int argumentType, String value) {
        getArgumentsList().add(new StringArgument(argumentType, value));
    }

    public void addArgument(int argumentType, int value) {
        getArgumentsList().add(new NumericArgument(argumentType, value));
    }

    public void addArgument(int argumentType) {
        getArgumentsList().add(new SingleItem(argumentType));
    }

    public void addArgument(int type, byte[] content) {
        getArgumentsList().add(new ByteArrayArgument(type, content));
    }

    public String getArgumentAsString(int type) {
        final List<Argument> argumentsList = getArgumentsList();
        for (final Argument argument : argumentsList) {
            if (argument.getType() == type) {
                return argument.getValueAsString();
            }
        }
        return null;
    }

    public int getArgumentAsInt(int type) {
        final List<Argument> argumentsList = getArgumentsList();
        for (final Argument argument : argumentsList) {
            if (argument.getType() == type) {
                return argument.getValueAsInt();
            }
        }
        return 0;
    }

    public boolean hasArgument(int type) {
        final List<Argument> argumentsList = getArgumentsList();
        for (final Argument argument : argumentsList) {
            if (argument.getType() == type) return true;
        }
        return false;
    }

    public void removeArgument(int type) {
        final List<Argument> argumentsList = getArgumentsList();
        for (int i = 0, n = argumentsList.size(); i < n; i++) {
            final Argument argument = argumentsList.get(i);
            if (argument.getType() == type) {
                argumentsList.remove(i);
                return;
            }
        }
    }

    protected void writeArgumentsTo(OutputStream outputStream) throws IOException {
        for (final Argument currentArgument : arguments) {
            currentArgument.writeTo(outputStream);
        }
    }

    protected int getLength() {
        final List<Argument> argumentsList = getArgumentsList();
        int length = 0;
        for (final Argument currentArgument : argumentsList) {
            length += currentArgument.getLength();
        }
        return length;
    }

    protected List<Argument> getArgumentsList() {
        return arguments;
    }

}