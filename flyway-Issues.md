# Migration

## Context
Whenever you make changes to state of DB, you have to write a new SQL script in `/resources/db` directory.

Let's say you wanna add new table in DB, you write a script `V14__Alter_Help_Thread_Metadata.sql`.

_Let's ignore commented SQL for now, say first iteration of your script only has first two entries_
![image](https://github.com/Together-Java/TJ-Bot/assets/61616007/1034a3ac-f7ce-45ca-b9c9-6917f5ef61ab)

 when you run the application for first time. Flyway will do the migration, i.e. keep track of that change and verify it stays same.

 On successful run of application, it will create a new entry in table `flyway_schema_history` of your local instance of DB.

![image](https://github.com/Together-Java/TJ-Bot/assets/61616007/f47961ce-452f-4a5c-a650-fed924ad4f2f)
_screenshot of what `flyway_schema_history` table looks like_

Now each time you run your application, it will verify these migrations using `checksum` from this table. 
Any changes made after migration, should be done via seperate script otherwise you run into migration errors.

Now under normal circumstances, once changes are made to production environment you would have to add a new SQL script for any new changes.

 But during development, requirements change frequently. Now you wanna add a new column in your newly created table. so you now add a couple in same SQL script.

![image](https://github.com/Together-Java/TJ-Bot/assets/61616007/8afb7019-ffb2-4dfd-9654-be7b377f0135)

_Notice new entries altering state of table_

Now if you try and run the application, flyway will throw migration error and you won't be able to run the application.

![image](https://github.com/Together-Java/TJ-Bot/assets/61616007/e01d6cc7-8399-4e4c-8da1-b6b43ef05195)

_Error message in logs should look like this_

## Solution
1. Open local DB instance, look at table `flyway_schema_history`.
2. Note down the version of last entry(should have name of your sql script).
3. Run this sql command in console for local DB, `delete from flyway_schema_history where version = 'VERSION_NUMBER_HERE';`.
4. Now drop the table/columns that are added via your new sql script using that DB console.
5. Once you revert back to old state of DB, it's safe to rewrite new SQL script with all the statements.
6. Run application, now it will create new entry `flyway_schema_history` and you should be able to run application.
 