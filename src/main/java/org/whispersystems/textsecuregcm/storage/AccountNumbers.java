/**
 * Copyright (C) 2013 Open WhisperSystems
 * <p>
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * <p>
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 * <p>
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.whispersystems.textsecuregcm.storage;

import org.skife.jdbi.v2.sqlobject.Bind;
import org.skife.jdbi.v2.sqlobject.SqlQuery;
import org.skife.jdbi.v2.sqlobject.SqlUpdate;
import org.skife.jdbi.v2.sqlobject.customizers.Mapper;

import java.util.Iterator;

public abstract class AccountNumbers {
    

    private static final String ID = "id";
    private static final String NUMBER = "number";
    private static final String BANDWIDTH_NUMBER = "bandwidth_number";
    private static final String ORDER_DATE = "order_date";

    @SqlUpdate("INSERT INTO account_numbers (" + NUMBER + ", " + BANDWIDTH_NUMBER + ", " + ORDER_DATE + ") VALUES (:number, :bandwidthNumber, now())")
    abstract void insertStep(@Bind("number") String number, @Bind("bandwidthNumber") String bandwidthNumber);

    @Mapper(Accounts.AccountMapper.class)
    @SqlQuery("SELECT * FROM account_numbers")
    public abstract Iterator<Account> getAll();

}
