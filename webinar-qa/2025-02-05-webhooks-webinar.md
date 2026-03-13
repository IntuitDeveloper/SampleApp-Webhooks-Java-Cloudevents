# Webhooks Webinar Q&A - Feb 5, 2025

## Q: Does the new 'time' property = the 'lastUpdated' that we currently get?

**Yes, they're equivalent.** Both represent when the change/event happened. The difference is formatting:
- Legacy `lastUpdated`: epoch milliseconds or varied timestamp format
- CloudEvents `time`: ISO 8601 format (e.g., `2025-09-10T21:31:25.179Z`)

---

## Q: How many events can be in a single webhook? What's the max size?

- **Up to 100 events** can be batched in a single webhook notification
- **Max payload size** is approximately 1MB
- Always iterate through the array - never assume a single event

---

## Q: Same webhook URL for old and new format? Do we need to handle both?

**Same URL, yes.** The webhook endpoint doesn't change when you switch formats.

**Do you need to handle both?** Not really. When you flip the CloudEvents toggle in the developer portal, that app only sends CloudEvents from that point forward. It's not like you'll randomly get both formats mixed together.

That said, if you're being cautious during migration, you can check which format came in:
- CloudEvents will have a `specversion` field
- Legacy has `eventNotifications` with `dataChangeEvent` inside

Most folks just update their code, flip the switch, and they're done.
