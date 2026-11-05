**OpenAS2 Developer Guide**

# Overview

## Source Code and Development Methodology

- Code is stored in Github: [https://github.com/OpenAS2/OpenAs2App/](https://github.com/OpenAS2/OpenAs2App/)

- Release packages are found in SourceForge: [https://sourceforge.net/projects/openas2/](https://sourceforge.net/projects/openas2/)

- Standard flow is to create a branch off “master” and raise commit back to that branch when ready.

- Code must be designed to be modular and reusable as far as possible.

- Code review and merge must be done by a different developer than the on(s) who made the code changes.

- Code style:

  - Indent 4 spaces. Use spaces to replace tabs for indenting.

  - Formatting of braces is not consistent but prefer braces on next line for all but array initializers

## Development Tools

- Make sure the IDE’s Java compiler is set up for the current lowest supported Java version for OpenAS2 compatibility.

## Development Process

- The project is built using Maven. To update the version number run this command:  
mvnw versions:set -DnewVersion=x.x.x  
The version number x.x.x can include SNAPSHOT  as a suffix (eg. 2.5.0SNAPSHOT3) for intermediate builds to ensure they are not confused with actual releases. The release version number will be updated in the Maven POM.

- The tests are run using this command: mvnw test

- The project is built using this command: mvnw clean package

## Application Documentation

- Use Libre Office to update the OpenAS2HowTo.odt as and when necessary.

- Remember to update the index table after completing the main document editing (right click on it and choose to update)

- Generate a PDF to the same folder once update is complete

- Upload the new version as an HTML file to the OpenAS2 website (see section below for steps)

## Logging

- Generally ***info*** level is left turned on so do not overuse the info level. It should be used to log key milestones in the AS2 message process and provide some user feedback for realtime monitoring

- Use ***trace*** level for fine grained logging such as detailed information on objects and state that help with code sequence execution verification

- Use ***debug*** level to provide information about key events not reported at info level

- For performance reasons, use either of the following for logging at ***debug*** or ***trace*** level as they are generally turned off:

  - use the placeholder formatting logging method calls:  
logger.debug(“AS2 file: \{\} Encoding: \{\} HTTP headers: \{\}”, msg.getFileEncoding(), msg.getHeaders());

OR

  - check for logging level using the ***logger.isDubugEnabled*** or l***ogger.isTraceEnabled*** calls for complicated logging

This avoids the costly process of building out a log message when it will not actually be used if that level of logging is turned off.

- Always append msg.getLogMsgID() to log calls that only pass a string to ensure logs are identified to the message they were from.

# Release Process

- Update the library versions to the latest stable releases: **mvnw versions:use-latest-releases**

- Set the release version eg. **mvnw versions:set -DnewVersion=4.15.0**

- Update the RELEASE-NOTES.md and README.md files to ensure the version number and release date are correctly set in both.

- **Push any changes to upstream and raise a PR to merge into the master branch.**

- **Create a release in the online repository:**

  - **Code → Tags → Releases → Draft New Release**

  - **Create a new tag in the format vx.x.x. eg v4.15.0**

  - **Select to auto generate release notes and then remove the auto generated commits in the release notes done by dependabot.**

  - **Click to make release.**

- **Pull latest master down and create the release package: mvnw clean package**

- **Upload files to Sourceforge:**

  - \*\*Go to project page: [https://sourceforge.net/projects/openas2/\*\*](https://sourceforge.net/projects/openas2/)

  - **Click the “Files” tab**

  - **Click “Add file”**

  - **Drag and drop or select the latest release package zip file (located in the Server/target/dist directory)**

  - **Drag and drop or select the RELEASE-NOTES.md from the project base directory.**

  - **Once the uploads complete successfully (can take a while sometimes), go back to the “Files” tab**

  - **Click the “i” icon for the README.md file and rename the file to RELEASE-NOTES-\<version number\>.md where \<version number\> is the prior version that was the latest release.**

  - **Click the “i” icon for the RELEASE-NOTES.md file and rename the file to README.md**

  - **Click the “i” icon for the newly uploaded zip file and check the “Select All” link and save.**

## Website Update

- Export the ***OpenAS2HowTo.odt*** as xhtml/html and name the file ***index.html***

- Host: **web.sourceforge.net**

- Port: **22**

- Protocol: **sftp**

- User Id: your normal sourceforge user

- Password: your normal sourceforge password

- Use FileZilla or similar to connect.

- Change directory to: /home/project-web/openas2/htdocs  
NOTE: the folder “openas2” will not be visible in the folder listing for /home/project-web/ and you must manually change to the destination folder

- Upload the index.html file exported from the how to document and overwrite the file in sourceforge

