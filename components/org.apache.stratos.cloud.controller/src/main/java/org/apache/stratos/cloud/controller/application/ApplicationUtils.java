/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.stratos.cloud.controller.application;

import java.util.regex.Pattern;

public class ApplicationUtils {

    public static boolean isAliasValid (String alias) {

        String patternString = "([a-z0-9]+([-][a-z0-9])*)+";
        Pattern pattern = Pattern.compile(patternString);

        return pattern.matcher(alias).matches();
    }

    public static boolean isValid (String arg) {

        if (arg == null || arg.isEmpty()) {
            return false;
        } else {
            return true;
        }
    }
}