# netbeans-l10n

To build your own localization module, use the german module as template. Copy "local_de" and rename it (e. g. "locale_fr"). The locale needs to be available below netbeans-l10n-zip/src/. Then adapt `<property name="locale" value="de"/>` in build.xml.

Build l10nantext before building the locale. 
