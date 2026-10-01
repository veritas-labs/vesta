# Vesta command messages

Direct-message payloads are UTF-8 JSON (JSON-LD, compact form). Vesta does **not** run a JSON-LD processor: use
this exact `@context` and these exact term names, or the message is ignored. Payloads over 16 KiB are rejected.

`CONTEXT` below is always:

```json
["https://www.w3.org/ns/activitystreams", {"schema": "https://schema.org/"}]
```

Every command has a unique `id` (use `urn:uuid:<new uuid>`); it is the idempotency key. Replies arrive on your
instance's `GET /direct/events`, flat JSON `{sender, machine_id, payload (base64), received_at, verified,
trust_decision}`; the reply's own `inReplyTo` matches your command's `id`. Only trust replies whose `sender` is
Vesta's agent id.

## List Homes — `Read` of `urn:vesta:homes`

```json
{"@context": CONTEXT, "type": "Read", "id": "urn:uuid:…",
 "object": {"type": "Collection", "id": "urn:vesta:homes"}}
```

Reply — an `Accept` whose `result` lists the Homes (`schema:identifier` is the bare Home id to use below):

```json
{"@context": CONTEXT, "type": "Accept", "id": "urn:uuid:…", "inReplyTo": "<your id>", "object": "<your id>",
 "result": {"type": "Collection", "id": "urn:vesta:homes", "totalItems": 2,
            "items": [{"type": "Object", "id": "urn:vesta:home:id-aaa",
                       "schema:identifier": "id-aaa", "schema:name": "The Johnsons"}, …]}}
```

Read-only, so it is answered freshly each time.

## Add to the Pantry — `Add` of a `schema:Product`

```json
{"@context": CONTEXT, "type": "Add", "id": "urn:uuid:…",
 "object": {
   "type": "schema:TypeAndQuantityNode",
   "schema:amountOfThisGood": 2,
   "schema:typeOfGood": {
     "type": "schema:Product",
     "schema:name": "Heinz Ketchup",
     "schema:category": ["Condiments", "Ketchup"],
     "schema:gtin": "5000157024671",
     "schema:size": "1 bottle"}},
 "target": {"type": "Collection", "id": "urn:vesta:document:<homeId>:pantry"}}
```

| Field | Rule |
| --- | --- |
| `id` | Required, non-blank, unique per command. |
| `target.id` | Required: `urn:vesta:document:<homeId>:pantry` with a Home id from the list. |
| `schema:name` | Required, non-blank. |
| `schema:category` | Optional. A string, or an array of 1–2 strings: `[primary, secondary]`. New item without one → category "Uncategorized". |
| `schema:gtin` | Optional barcode. Items are matched by barcode if present, else by name (case-insensitive). |
| `schema:size` | Optional package size — see [Package size](#package-size). |
| `schema:amountOfThisGood` | Optional integer 1–9999, default 1. **Added to** the current stock. |

If the item already exists, omitted optional fields keep their existing values.

Reply on success:

```json
{"@context": CONTEXT, "type": "Accept", "id": "urn:uuid:…", "inReplyTo": "<your id>", "object": "<your id>",
 "result": {"type": "schema:Product", "id": "<pantry key>", "schema:name": "Heinz Ketchup",
            "schema:amountOfThisGood": 3}}
```

`result.schema:amountOfThisGood` is the stock **after** your add.

## Rejections

`{"type": "Reject", "inReplyTo": "<your id>", "summary": "<human-readable reason>", …}`. Summaries include:

- `not approved: ask the user to approve this agent in the Vesta app, then retry`
- `unknown home`
- `quantity out of range`, `title is required`, `type must be Add or Read`, `target.id must be …`, `payload too large`, …

## Silence

Vesta never replies to messages its x0x daemon doesn't vouch for (sender unverified, or not trusted), nor to JSON
that isn't one of these commands. No reply at all almost always means missing trust in one direction, or the app
not running.

## Package size

Send `schema:size` as `<amount><unit> <container>` — an amount, then a unit and/or a container. Vesta tidies what you
send into its canonical form and echoes the stored value back as `result."schema:size"` in the `Accept`.

| Part | Values (aliases such as `litre`, `ounce`, `can`, plurals and any case are accepted) |
| --- | --- |
| amount | a number: `500`, `1.5` |
| unit | `g` `kg` `ml` `l` `oz` `lb` `fl oz` `pint` `gal` |
| container | `tin` `jar` `bottle` `packet` `box` `bag` `carton` `tub` `tube` `pouch` `sachet` `tray` `roll` `pack` |

Good: `500ml`, `1kg bag`, `15 oz can` (stored as `15 oz tin`), `6 pack`, `1 bottle`. Use a count with a container
(`6 pack`) when there is no weight or volume. Text Vesta doesn't recognise is still stored (trimmed), not rejected, but
it won't appear in the app's pickers — stick to the grammar. Omit `schema:size` for loose items with no packaging (apples, a bunch of bananas) or if you don't know it.
