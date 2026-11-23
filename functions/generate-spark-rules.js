const { writeFileSync } = require("node:fs");
const { join } = require("node:path");

const all = (...conditions) => conditions.map(condition => "(" + condition + ")").join(" && ");
const any = (...conditions) => conditions.map(condition => "(" + condition + ")").join(" || ");
const child = (node, key) => node + ".child(" + key + ")";
const field = (node, name) => child(node, "'" + name + "'");
const value = (node, name) => field(node, name) + ".val()";
const parents = (levels) => "newData" + ".parent()".repeat(levels);
const same = (...names) => all(...names.map(name => value("newData", name) + " === " + value("data", name)));
const present = (...names) => "newData.hasChildren(" + JSON.stringify(names).replaceAll('"', "'") + ")";
const text = (minimum, maximum) =>
  "newData.isString() && newData.val().length >= " + minimum + " && newData.val().length <= " + maximum +
  (minimum > 0 ? " && !newData.val().matches(/^ *$/)" : "");
const integer = (minimum, maximum) =>
  "newData.isNumber() && newData.val() % 1 === 0 && newData.val() >= " + minimum + " && newData.val() <= " + maximum;
const oneOf = (...options) => any(...options.map(option => "newData.val() === '" + option + "'"));
const shape = (required, optional = {}, extra = []) => ({
  ".validate": all(present(...Object.keys(required)), ...extra),
  ...Object.fromEntries(Object.entries({ ...required, ...optional }).map(([name, constraint]) => [
    name, typeof constraint === "string" ? { ".validate": constraint } : constraint
  ])),
  "$extra": { ".validate": false }
});

const authenticated = "auth != null";
const verified = all(authenticated, "auth.token.email_verified === true");
const own = all(verified, "auth.uid === $uid");
const accountAt = (location, uid) => child(field(location, "accounts"), uid);
const requestAt = (location, uid) => child(field(location, "joinRequests"), uid);
const communityAt = (location, key) => child(field(location, "communityData"), key);
const memberAt = (location, community, uid) => child(field(communityAt(location, community), "members"), uid);
const account = accountAt("root", "auth.uid");
const actor = memberAt("root", "$community", "auth.uid");
const authorized = (community) => {
  const active = memberAt("root", community, "auth.uid");
  return all(verified, value(account, "community") + " === " + community,
    value(active, "active") + " === true", value(active, "role") + " === " + value(account, "role"));
};
const administrator = (community) => all(authorized(community), "auth.token.role === 'admin'",
  "auth.token.community === " + community, value(account, "role") + " === 'admin'");
const member = authorized("$community");
const admin = administrator("$community");
const subject = accountAt("root", "$uid");
const request = requestAt("root", "$uid");
const pending = all(value(subject, "role") + " === 'neighbor'", value(subject, "community") + " === ''");
const name = text(2, 100);
const unit = text(1, 30);
const email = text(3, 254);
const serverTime = "newData.val() === now";
const validKey = "newData.isString() && newData.val().matches(/^[A-Za-z0-9_-]{1,120}$/)";
const activeSubject = (community) => memberAt("root", community, "$uid");

const accounts = {
  "$uid": {
    ".read": own,
    ".write": all(authenticated, "auth.uid === $uid",
      "auth.token.email != null", "!data.exists()", "newData.exists()",
      "newData.child('community').val() === ''", "newData.child('role').val() === 'neighbor'"),
    ...shape({ name, unit, community: any("newData.val() === ''", validKey), role: oneOf("neighbor", "admin") }),
  }
};
const accountCommunity = "data.parent().child('community').val()";
accounts.$uid.name[".write"] = all(own, authorized(accountCommunity), "data.exists()", "newData.exists()",
  "newData.val() === " + value(memberAt(parents(3), accountCommunity, "$uid"), "name"));
accounts.$uid.community[".write"] = all(pending, "newData.isString()", administrator("newData.val()"),
  value(request, "community") + " === newData.val()",
  "!" + activeSubject("newData.val()") + ".exists()",
  memberAt(parents(3), "newData.val()", "$uid") + ".exists()");

const joinRequests = {
  "$uid": {
    ".read": any(own, all("data.exists()", administrator(value("data", "community")))),
    ".write": all(own, pending, "newData.exists()",
      field(communityAt("root", value("newData", "community")), "members") + ".exists()",
      value("newData", "name") + " === " + value(subject, "name"),
      value("newData", "unit") + " === " + value(subject, "unit"),
      value("newData", "email") + " === auth.token.email"),
    ...shape({ community: validKey, name, unit, email })
  }
};

const bounded = "query.limitToLast != null && query.limitToLast <= 100";
const latest = all("query.orderByChild === 'createdAt'", bounded);
const members = {
  ".read": all(admin, "query.orderByKey",
    any(bounded, "query.limitToFirst != null && query.limitToFirst <= 100")),
  "$uid": {
    ".read": any(admin, all(own, value(subject, "community") + " === $community")),
    ".write": all(admin, pending, "!data.exists()", "newData.exists()",
      value(request, "community") + " === $community", value(request, "name") + " === " + value(subject, "name"),
      value(request, "unit") + " === " + value(subject, "unit"),
      value(accountAt(parents(4), "$uid"), "community") + " === $community",
      value("newData", "role") + " === 'neighbor'", value("newData", "active") + " === true",
      ...["name", "unit", "email"].map(key => value("newData", key) + " === " + value(request, key))),
    ...shape({ name, unit, email, role: oneOf("neighbor", "admin"), active: "newData.isBoolean()" }),
  }
};
members.$uid.name[".write"] = all(own, member, "data.exists()", "newData.exists()",
  "newData.val() === " + value(accountAt(parents(5), "$uid"), "name"));
members.$uid.active[".write"] = all(admin, "data.exists()", "newData.isBoolean()",
  "data.parent().child('role').val() === 'neighbor'", value(subject, "role") + " === 'neighbor'",
  value(subject, "community") + " === $community");

const attribution = { owner: text(1, 128), author: name, unit, createdAt: integer(1, 9007199254740991) };
const created = all("!data.exists()", "newData.exists()", "$record.beginsWith(auth.uid + '_')",
  "$record.matches(/^[A-Za-z0-9_-]{1,170}$/)",
  value("newData", "owner") + " === auth.uid", value("newData", "author") + " === " + value(actor, "name"),
  value("newData", "unit") + " === " + value(actor, "unit"), value("newData", "createdAt") + " === now");
const attributed = same(...Object.keys(attribution));
const record = (access, properties, optional, extra) => ({
  ".read": member, ".write": access, ...shape({ ...attribution, ...properties }, optional, extra)
});
const feed = (rule) => ({ ".read": all(member, latest), ".indexOn": ["createdAt"], "$record": rule });
const messages = feed(record(all(member, created), { text: text(1, 1000), community: "newData.val() === $community" }));
const edit = (access, ...fixed) => all(access, any(created, all("data.exists()", any("!newData.exists()", all(attributed, ...fixed)))));
const marketAccess = all(member, "!data.exists() || data.child('owner').val() === auth.uid");
const marketplace = feed(record(edit(marketAccess), {
  title: text(1, 100), description: text(1, 1500), price: integer(0, 1000000000),
  category: oneOf("home", "technology", "clothing", "services", "other"),
  status: oneOf("available", "reserved", "sold")
}));
const announcements = feed(record(
  all(edit(admin, same("notificationDispatchedAt")), "data.exists() || !newData.child('notificationDispatchedAt').exists()"),
  { title: text(1, 100), description: text(1, 3000), priority: oneOf("info", "important", "urgent"), status: oneOf("published") },
  { notificationDispatchedAt: integer(1, 9007199254740991) }
));

const alertFixed = ["category", "description", "community", "delivery", "brokerAcknowledgedAt"];
const alertUpdated = all(admin, "data.exists()", "newData.exists()", attributed, same(...alertFixed),
  "data.child('status').val() !== 'closed'", "newData.child('status').val() !== 'active'",
  "newData.child('statusChangedAt').val() === now", "newData.child('statusChangedBy').val() === auth.uid");
const alerts = feed(record(any(all(member, created, "newData.child('status').val() === 'active'",
  "newData.child('delivery').val() === 'pending'",
  ...["statusChangedAt", "statusChangedBy", "brokerAcknowledgedAt"].map(key => "!newData.child('" + key + "').exists()")),
  alertUpdated), {
  category: oneOf("security", "fire", "accident", "medical", "other"), description: text(0, 1000),
  community: "newData.val() === $community", status: oneOf("active", "attended", "closed"),
  delivery: oneOf("pending", "published")
}, { statusChangedAt: integer(1, 9007199254740991), statusChangedBy: text(1, 128), brokerAcknowledgedAt: integer(1, 9007199254740991) }));

const reportStates = oneOf("pending", "working", "resolved");
const reportUpdate = (previous, next, changeId) => {
  const event = child(field(next, "history"), changeId);
  return all(admin, previous + ".exists()", next + ".exists()",
    value(next, "lastChangeId") + " === " + changeId, "!" + child(field(previous, "history"), changeId) + ".exists()",
    event + ".exists()", value(event, "status") + " === " + value(next, "status"),
    value(event, "author") + " === " + value(actor, "name"), value(event, "createdAt") + " === now");
};
const report = record(all(member, created, "newData.child('status').val() === 'pending'",
  "!newData.child('lastChangeId').exists()", "!newData.child('history').exists()"), {
  title: text(1, 100), description: text(1, 2000), location: text(1, 120), status: reportStates,
  category: oneOf("lighting", "cleaning", "leak", "elevator", "garden", "security", "other")
}, {
  lastChangeId: validKey,
  history: {
    "$changeId": {
      ".write": all("$changeId.matches(/^[A-Za-z0-9_-]{1,120}$/)",
        reportUpdate("data.parent().parent()", parents(2), "$changeId")),
      ...shape({ status: reportStates, note: text(0, 1500), author: name, createdAt: serverTime }, {},
        ["newData.child('status').val() !== 'resolved' || !newData.child('note').val().matches(/^ *$/)"])
    }
  }
});
report[".read"] = any(admin, all(member, "data.child('owner').val() === auth.uid"));
for (const property of ["status", "lastChangeId"]) {
  report[property][".write"] = reportUpdate("data.parent()", parents(1), value(parents(1), "lastChangeId"));
}
const reports = {
  ".read": any(all(admin, latest), all(member, "query.orderByChild === 'owner'",
    "query.equalTo === auth.uid || (query.startAt === auth.uid && query.endAt === auth.uid)", bounded)),
  ".indexOn": ["createdAt", "owner"],
  "$record": report
};

const options = Object.fromEntries(Array.from({ length: 6 }, (_, optionIndex) => [String(optionIndex),
  { ".validate": optionIndex < 2 ? text(1, 100) : all(text(1, 100), parents(1) + ".child('" + (optionIndex - 1) + "').exists()") }]));
const pollRule = record(all(admin, created), {
  title: text(1, 150),
  options: { ".validate": present("0", "1"), ...options, "$extra": { ".validate": false } },
  closesAt: integer(1, 9007199254740991) + " && newData.val() >= now + 60000 && newData.val() <= now + 7776000000",
  showResults: "newData.isBoolean()", closed: "newData.isBoolean()"
}, { sparkVersion: "newData.val() === 1" });
pollRule.closed = {
  ".write": all(admin, "data.val() === false", "newData.val() === true"),
  ".validate": "newData.isBoolean()"
};
pollRule[".write"] = all(pollRule[".write"], "newData.child('closed').val() === false",
  "newData.child('sparkVersion').val() === 1");
const polls = feed(pollRule);
const poll = child(field(communityAt("root", "$community"), "polls"), "$poll");
const voting = all(member, value(poll, "sparkVersion") + " === 1", value(poll, "closed") + " === false",
  "now < " + value(poll, "closesAt"));
const ballots = (community) => field(child(field(community, "pollLedger"), "$poll"), "votes");
const totals = (community) => child(field(community, "pollTotals"), "$poll");
const previousTotal = (choice) => child(totals(communityAt("root", "$community")), choice);
const countIncreased = (previous, next) => any(all("!" + previous + ".exists()", next + ".val() === 1"),
  all(previous + ".isNumber()", next + ".val() === " + previous + ".val() + 1"));
const selected = "(newData.val() + '')";
const pollLedger = {
  "$poll": {
    "votes": {
      "$uid": {
        ".read": all(member, "auth.uid === $uid"),
        ".write": all(voting, "auth.uid === $uid", "!data.exists()", integer(0, 5),
          child(field(poll, "options"), selected) + ".exists()",
          countIncreased(previousTotal(selected), child(totals(parents(4)), selected)))
      }
    }
  }
};
const pollTotals = {
  "$poll": {
    ".read": all(member, value(poll, "sparkVersion") + " === 1",
      any(admin, value(poll, "showResults") + " === true")),
    "$choice": {
      ".write": all(voting, child(field(poll, "options"), "$choice") + ".exists()",
        "!" + child(ballots(communityAt("root", "$community")), "auth.uid") + ".exists()",
        "(" + child(ballots(parents(3)), "auth.uid") + ".val() + '') === $choice",
        countIncreased("data", "newData"))
    }
  }
};

const devices = {
  "$uid": {
    "$hash": {
      ".write": all(member, "auth.uid === $uid", "$hash.matches(/^[a-f0-9]{64}$/)"),
      ...shape({ token: text(16, 4096), updatedAt: serverTime })
    }
  }
};
const mqttAccess = {
  "$uid": {
    ".read": all(own, authorized(value(account, "community")),
      "!data.exists() || data.child('community').val() === " + value(account, "community"))
  }
};

const rules = {
  ".read": false, ".write": false, accounts, joinRequests, mqttAccess,
  communityData: { "$community": {
    metadata: { displayName: { ".read": member } },
    members, messages, marketplace, announcements, alerts, reports, polls, pollLedger, pollTotals, devices
  } }
};
writeFileSync(join(__dirname, "..", "database.rules.json"), JSON.stringify({ rules }, null, 2) + "\n");
