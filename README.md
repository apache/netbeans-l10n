<!---

    Licensed to the Apache Software Foundation (ASF) under one
    or more contributor license agreements.  See the NOTICE file
    distributed with this work for additional information
    regarding copyright ownership.  The ASF licenses this file
    to you under the Apache License, Version 2.0 (the
    "License"); you may not use this file except in compliance
    with the License.  You may obtain a copy of the License at

      http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing,
    software distributed under the License is distributed on an
    "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
    KIND, either express or implied.  See the License for the
    specific language governing permissions and limitations
    under the License.

-->

# netbeans-l10n

To build your own localization module, use the german module as template. The locale needs to be available below netbeans-l10n-zip/src/.
## Steps to build your own localization module:
* Copy "local_de" and rename it (e. g. "locale_fr").
* Change the bundle dir in `src/org/apache/netbeans/l10n` from `de` to your locale (e. g. `fr`).
* Change module name in  `src/org/apache/netbeans/l10n/Bundle.properties` to your locale (e. g. `French`).
* In file `manifest.mf` change 
	* line `OpenIDE-Module: org.apache.netbeans.l10n.de` to your locale (e. g. `org.apache.netbeans.l10n.fr`)
	* line `OpenIDE-Module-Localizing-Bundle: org/apache/netbeans/l10n/de/Bundle.properties` to your locale (`org/apache/netbeans/l10n/fr/Bundle.properties`)
* In file `build.xml` change 
	* line `<project name="org.apache.netbeans.l10n.de" default="netbeans" basedir=".">` to your locale
	* line `<description>Builds, tests, and runs the project org.apache.netbeans.l10n.de.</description>` to your locale
	* line `<property name="locale" value="de"/>` to your locale
* In file `nbproject/build-impl.xml`
	* Adapt line `<project name="org.apache.netbeans.l10n.de-impl" basedir="..">`
	* Adapt line `<code-name-base>org.apache.netbeans.l10n.de</code-name-base>`


Build l10nantext before building the locale. 
