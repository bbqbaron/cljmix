# cljmix

Marvel's Unlimited UI wasn't meeting my needs for following a certain set of characters (the X-Men), so I cobbled together a solution. This uses [Lacinia](https://github.com/walmartlabs/lacinia) to serve a GQL schema generated from the Marvel API (this level of indirection wasn't necessary--ie I could have just queried Marvle directly--but I felt like tackling automated OpenAPI-GQL translation, and wanted to learn Lacinia). You can then subscribe to a set of characters and be given a publication-sorted list of comics in which they appear.

The UI is ugly and unintuitive; I threw it together to expose information in a direct, raw form. Designers welcome :).

You could theoretically host and run this yourself, but:

## Caveat

The [Prevayler](https://github.com/klauswuestefeld/prevayler-clj) db I threw in to speed up queries does not currently TTL the results of API calls. I believe it is therefore in violation of the Marvel ToS, since caching may not be indefinite. **Therefore, run this at your risk**.

## Prerequisites

Before you get started, you will need:

1. Leiningen, which you can install by following instructions on [their site](https://leiningen.org/#install) or using [Homebrew](https://formulae.brew.sh/formula/leiningen)
1. [Java 8+](https://adoptopenjdk.net/)
1. A [developer API key from Marvel](https://developer.marvel.com/)
1. [Node and npm](https://nodejs.org/en/download/)

## Dev Setup

1. Add your keys to a `private.edn` resource structured like [the example](#resources/example-creds.edn)
1. `npm install`
1. `lein cljsbuild auto`

- TODO add figwheel

From the `lein repl`:

1. Call `sg/create-schema` in `user` to generate the GraphQL schema from the Marvel JSON schema.
1. Call `start` in `user` namespace

The project UI will be exposed on `localhost:8292/index.html`
