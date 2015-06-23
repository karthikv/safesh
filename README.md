# safesh
`safesh` stores and manages encrypted secrets using SSH keys.

## Installation
Download the `safesh` binary from the
[latest release](https://github.com/karthikv/safesh/releases/tag/v0.1.0) and,
optionally, put it in a directory in your `PATH`.

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
array of members that can read the secret. members are either groups or key
names in the keys/ directory.

By default, `safesh` assumes the configuration file is ./config.yml, the
permissions file is ./permissions.yml, and the keys directory is ./keys. You
can configure these by passing the -c, -p, and -k options, respectively, to
`safesh`.


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

Given no options, this will prompt you to enter new plain text for the secret
named `[name]`. It will then encrypt and release this secret to all those who
have permissions to access it.


## Read a secret
To read a secret, run:

```sh
$ safesh cat [name]
```

This will decrypt the secret named `[name]` and output the plain text to stdout.


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
$ safesh update -r
```

The `-r` option tells the `update` command to only release/revoke the secret,
and not change its contents.
