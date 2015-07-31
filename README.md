# safesh
`safesh` stores and manages encrypted secrets using SSH keys.

## Installation
Download the `safesh` binary from the
[latest release](https://github.com/karthikv/safesh/releases/tag/v0.1.0). Run
`chmod +x safesh` to make it executable. Optionally, put it in a directory in
your `PATH`.

## Configuration
When you run `safesh` for the first time, it'll ask you setup questions and
create a config.yml in the current directory.

You need to manually create a keys directory and a permissions.yml file. Your
current working directory structure should look like this:

```
config.yml
permissions.yml
keys/
  - john@doe.com
```

Put all SSH public keys in the keys folder.

The permissions.yml file should be in the following format:

```yml
groups:
  group-name:
    - key1
    - key2
    - key3

secrets:
  secret-name:
    - member1
    - member2
    - member3
```

permissions.yml has two hashes: groups and secrets.

groups are lists of people that you can release secrets to. groups are
represented as a hash where the key is a group name and the value is an array
of key names in the keys/ directory.

secrets are the actual secrets that you can create/read/update/delete. secrets
are represented as a hash where the key is a secret name and the value is an
array of members that can read the secret. members are either group names or
key names in the keys/ directory.

By default, `safesh` assumes the configuration file is ./config.yml, the
permissions file is ./permissions.yml, and the keys directory is ./keys. You
can configure these by passing the -c, -p, and -k options, respectively, to
`safesh`.

Each time you run `safesh` successfully, it records the config file,
permissions file, and keys directory you specified. If you run `safesh` again
later, and you don't point it at a valid config file (i.e. -c is not specified
and your directory doesn't contain config.yml, or -c is specified and it's an
invalid path), then it'll use the saved paths. Hence, you don't need to cd into
your secrets directory each time you use `safesh`.

## Secret storage
Secrets are stored within a secret directory, which, by default, is the current
working directory. You can modify this by passing the -s option to `safesh`.

Within the secrets directory is a subdirectory for each public key. Contained
within the subdirectory for a key are the secrets that are encrypted using that
key. The owner of a private key can read secrets within the directory that
corresponds to his/her public key.

The secrets directory, keys directory, and permissions.yml should all be
version controlled. config.yml is user-specific and should be left out of
a repository. Whenever a secret is stored, updated, released, or revoked,
a commit should be made to the version control system. This way, there's
a history of all secrets, and any team member can simply update the directory
from version control to get new secrets.


## Store/Update a secret
To store/update a secret, first update your permissions.yml file with the new
secret name and appropriate permissions. For instance, if you wanted to create
a secret foo and release it to john@doe.com and bar@baz.com, you'd write:

```yml
secrets:
  foo:
    - john@doe.com
    - bar@baz.com
```

into your permissions.yml file. Then, run the following command:

```sh
$ safesh update [name]
```

This will prompt you to enter new plain text for the secret named `[name]`. It
will then encrypt and release this secret to all those who have permissions to
access it. If you'd like, you may specify additional `[name]`s to update
multiple secrets at once.


## Read a secret
To read a secret, run:

```sh
$ safesh cat [name]
```

This will decrypt the secret named `[name]` and output the plain text to stdout.
Again, you may specify additional `[name]` arguments to output multiple secrets.


## List secrets
To list all secrets that can be decrypted, run:

```sh
$ safesh ls
```


## Change permissions for a secret
Say you have a secret foo in your permissions file:

```yml
secrets:
  foo:
    - john@doe.com
    - bar@baz.com
```

If you want to revoke foo from john@doe.com, first update the permissions:

```yml
secrets:
  foo:
    - bar@baz.com
```

Then, run the following command:

```sh
$ safesh update -r foo
```

The `-r` option tells the `update` command to only release/revoke `foo`,
and not change its contents.

To release/revoke all secrets to match the permissions file, simply run:

```sh
$ safesh update -r all
```

Note that this will only release/revoke secrets that you have access to.


## Delete a secret
If you'd like to delete a secret entirely, run:

```sh
$ safesh delete [name]
```

Make sure to remove the secret from your permissions file afterwards.

You may specify multiple `[name]` arguments to delete multiple secrets.


## Fetch secrets to local filesystem
Given a YAML file mapping secret names to filesystem paths:

```yml
secret1: some/path/secret1
secret2: some/other/path/secret2
```

You can fetch each secret to its corresponding filesystem location by running:

```sh
$ safesh fetch [path]
```

where `[path]` is the path to the YAML file. `[path]` defaults to ./safesh.yml.
