package com.caproverforge.testing

import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.RecordedRequest
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.concurrent.CopyOnWriteArrayList

/**
 * A stand-in for a CapRover server (API v2) with a small demo deployment.
 * Responses use CapRover's real envelope: {"status":100,"description":...,"data":...}.
 */
class FakeCapRover : Dispatcher() {
    val requests = CopyOnWriteArrayList<Pair<String, String>>() // "METHOD path" to body
    var token = "demo-token"
    var password = "hunter2"
    var expireToken = false

    private val now = Instant.now()
    private fun ago(minutes: Long) = now.minus(minutes, ChronoUnit.MINUTES).toString()

    override fun dispatch(request: RecordedRequest): MockResponse {
        val url = request.url
        val path = url.encodedPath.removePrefix("/api/v2")
        val body = request.body?.utf8().orEmpty()
        requests += "${request.method} $path" to body

        if (path == "/login") {
            return if (body.contains("\"password\":\"$password\"")) ok("""{"token":"$token"}""")
            else envelope(1105, "Password is incorrect.", "{}")
        }
        if (expireToken || request.headers["x-captain-auth"] != token) {
            return envelope(1106, "Auth token corrupted", "{}")
        }
        return when {
            path == "/user/system/info" -> ok("""{"hasRootSsl":true,"forceSsl":true,"rootDomain":"example.com","captainSubDomain":"captain"}""")
            path == "/user/system/versioninfo" && request.method == "GET" ->
                ok("""{"currentVersion":"1.14.1","latestVersion":"1.15.0","canUpdate":true,"changeLogMessage":"- Projects can be nested\n- Faster one-click deployments\n- Security fixes"}""")
            path == "/user/system/loadbalancerinfo" ->
                ok("""{"activeConnections":42,"accepted":918273,"handled":918273,"total":2716345,"reading":0,"writing":3,"waiting":39}""")
            path == "/user/system/nodes" -> ok(
                """{"nodes":[{"nodeId":"k3x9w2leader","type":"manager","isLeader":true,"hostname":"captain-01","architecture":"x86_64",
                "operatingSystem":"Ubuntu 24.04.3 LTS","nanoCpu":4000000000,"memoryBytes":8323616768,"dockerEngineVersion":"28.4.0",
                "ip":"203.0.113.10","state":"ready","status":"ready"}]}"""
            )
            path == "/user/apps/appDefinitions" -> ok(appsJson())
            path == "/user/projects" -> ok("""{"projects":[{"id":"p-blog","name":"Blog","description":"WordPress and its database"},{"id":"p-api","name":"Backend","description":""}]}""")
            path.matches(Regex("/user/apps/appData/[^/]+/logs")) -> ok("""{"logs":"${appLogsHex()}"}""")
            path.matches(Regex("/user/apps/appData/[^/]+")) && request.method == "GET" -> ok(
                """{"isAppBuilding":false,"isBuildFailed":false,"logs":{"firstLineNumber":0,"lines":[
                "Build started for api","Cloning into 'api'...","Step 1/6 : FROM node:22-alpine","Step 2/6 : WORKDIR /app",
                "Step 3/6 : COPY package*.json ./","Step 4/6 : RUN npm ci --omit=dev","added 214 packages in 9s",
                "Step 5/6 : COPY . .","Step 6/6 : CMD [\"node\",\"server.js\"]","Successfully built 7d2f1c9a0b11",
                "Build has finished successfully!"]}}"""
            )
            path == "/user/oneclick/template/list" -> ok(oneClickJson())
            path == "/user/oneclick/template/app" -> ok(templateJson())
            path == "/user/oneclick/repositories" -> ok("""{"urls":[]}""")
            path == "/user/registries" -> ok(
                """{"registries":[{"id":"r1","registryUser":"deploy","registryPassword":"x","registryDomain":"ghcr.io",
                "registryImagePrefix":"example","registryType":"REMOTE_REG"}],"defaultPushRegistryId":"r1"}"""
            )
            path == "/user/system/diskcleanup" -> ok("""{"mostRecentLimit":2,"cronSchedule":"0 3 * * 0","timezone":"Europe/London"}""")
            path == "/user/apps/appDefinitions/unusedImages" -> ok(
                """{"unusedImages":[{"id":"sha256:1f2e3d4c5b6a79881f2e3d4c","tags":["img-captain-api:5"]},
                {"id":"sha256:9a8b7c6d5e4f30219a8b7c6d","tags":["img-captain-api:4"]}]}"""
            )
            path == "/user/system/netdata" -> ok("""{"isEnabled":true,"netDataUrl":"captain.example.com/net-data-captain","data":{}}""")
            path == "/user/system/nginxconfig" -> ok("""{"baseConfig":{"byDefault":"worker_processes auto;","customValue":""},"captainConfig":{"byDefault":"server {}","customValue":""}}""")
            path == "/user/system/createbackup" -> ok("""{"downloadToken":"tok/en+1"}""")
            request.method == "POST" -> ok("{}")
            else -> envelope(1000, "Not found in fake: $path", "{}")
        }
    }

    private fun ok(data: String) = envelope(100, "OK", data)

    private fun envelope(status: Int, description: String, data: String) =
        MockResponse.Builder()
            .code(200)
            .addHeader("Content-Type", "application/json")
            .body("""{"status":$status,"description":"$description","data":$data}""")
            .build()

    private fun appsJson(): String {
        fun versions(n: Int, image: String, startMinutesAgo: Long) = (0..n).joinToString(",") { v ->
            """{"version":$v,"deployedImageName":"$image:$v","timeStamp":"${ago(startMinutesAgo - v * 600L)}","gitHash":"${"a1b2c3d4e5f6".take(7)}$v"}"""
        }
        return """{
          "rootDomain":"example.com","captainSubDomain":"captain","defaultNginxConfig":"server {\n  listen 80;\n}",
          "appDefinitions":[
            {"appName":"api","projectId":"p-api","description":"Public REST API","deployedVersion":5,"notExposeAsWebApp":false,
             "hasPersistentData":false,"hasDefaultSubDomainSsl":true,"containerHttpPort":3000,"captainDefinitionRelativeFilePath":"./captain-definition",
             "forceSsl":true,"websocketSupport":true,"instanceCount":3,"customDomain":[{"publicDomain":"api.acme.io","hasSsl":true}],
             "tags":[{"tagName":"node"},{"tagName":"prod"}],"ports":[],"volumes":[],
             "envVars":[{"key":"NODE_ENV","value":"production"},{"key":"DATABASE_URL","value":"postgres://api@srv-captain--api-db:5432/api"},{"key":"LOG_LEVEL","value":"info"}],
             "versions":[${versions(5, "img-captain-api", 3100)}],
             "appPushWebhook":{"tokenVersion":"t1","pushWebhookToken":"webhook-token-123","repoInfo":{"repo":"github.com/example/api","branch":"main","user":"deploy-bot","password":"ghp_x","sshKey":""}},
             "appDeployTokenConfig":{"enabled":false},"isAppBuilding":false,"networks":["captain-overlay-network"]},
            {"appName":"api-db","projectId":"p-api","deployedVersion":0,"notExposeAsWebApp":true,"hasPersistentData":true,"hasDefaultSubDomainSsl":false,
             "instanceCount":1,"customDomain":[],"ports":[],"volumes":[{"containerPath":"/var/lib/postgresql/data","volumeName":"api-db-data"}],
             "envVars":[{"key":"POSTGRES_PASSWORD","value":"s3cret"}],"versions":[${versions(0, "postgres", 9000)}],"isAppBuilding":false},
            {"appName":"blog","projectId":"p-blog","description":"Company blog","deployedVersion":2,"notExposeAsWebApp":false,"hasPersistentData":true,
             "hasDefaultSubDomainSsl":true,"forceSsl":true,"instanceCount":1,"customDomain":[{"publicDomain":"blog.example.com","hasSsl":true},{"publicDomain":"www.example.com","hasSsl":false}],
             "ports":[],"volumes":[{"containerPath":"/var/www/html","volumeName":"blog-wp-data"}],"envVars":[],"versions":[${versions(2, "wordpress", 20000)}],"isAppBuilding":false},
            {"appName":"docs","deployedVersion":1,"hasDefaultSubDomainSsl":true,"instanceCount":1,"customDomain":[],"ports":[],"volumes":[],"envVars":[],
             "versions":[{"version":0,"deployedImageName":"img-captain-docs:0","timeStamp":"${ago(400)}"},{"version":1,"deployedImageName":"img-captain-docs:1","timeStamp":"${ago(40)}"},{"version":2,"timeStamp":"${ago(5)}"}],
             "isAppBuilding":true},
            {"appName":"worker","deployedVersion":3,"notExposeAsWebApp":true,"instanceCount":0,"customDomain":[],"ports":[{"containerPort":9100,"hostPort":9100,"protocol":"tcp"}],
             "volumes":[],"envVars":[],"versions":[${versions(3, "img-captain-worker", 50000)}],"isAppBuilding":false},
            {"appName":"sandbox","deployedVersion":0,"instanceCount":1,"customDomain":[],"ports":[],"volumes":[],"envVars":[],
             "versions":[{"version":0,"deployedImageName":"caprover/caprover-placeholder-app:latest","timeStamp":"${ago(2)}"}],"isAppBuilding":false}
          ]}"""
    }

    private fun appLogsHex(): String {
        val lines = listOf(
            "2026-10-09T09:58:01.120Z Server listening on :3000",
            "2026-10-09T09:58:04.511Z GET /health 200 2ms",
            "2026-10-09T09:58:09.007Z GET /v1/orders?limit=20 200 38ms",
            "2026-10-09T09:58:12.441Z POST /v1/orders 201 61ms",
            "2026-10-09T09:58:13.902Z WARN slow query: SELECT * FROM orders WHERE customer_id = \$1 (412ms)",
            "2026-10-09T09:58:20.010Z GET /health 200 1ms",
        )
        val out = java.io.ByteArrayOutputStream()
        lines.forEachIndexed { i, line ->
            val payload = "$line\n".toByteArray()
            val stream = if (i == 4) 2 else 1
            out.write(byteArrayOf(stream.toByte(), 0, 0, 0, 0, 0, (payload.size shr 8).toByte(), payload.size.toByte()))
            out.write(payload)
        }
        return out.toByteArray().joinToString("") { "%02x".format(it) }
    }

    private fun oneClickJson(): String {
        val apps = listOf(
            Triple("wordpress", "WordPress", "The most popular blogging and website platform"),
            Triple("postgres", "PostgreSQL", "Powerful, open source object-relational database"),
            Triple("n8n", "n8n", "Workflow automation for technical people"),
            Triple("uptime-kuma", "Uptime Kuma", "A fancy self-hosted monitoring tool"),
            Triple("plausible", "Plausible Analytics", "Simple, privacy-friendly web analytics"),
            Triple("gitea", "Gitea", "Painless self-hosted Git service"),
        )
        return """{"oneClickApps":[${apps.joinToString(",") { (n, d, desc) ->
            """{"name":"$n","displayName":"$d","description":"$desc","logoUrl":"","baseUrl":"https://oneclickapps.caprover.com","isOfficial":true}"""
        }}]}"""
    }

    private fun templateJson() = """{"appTemplate":{"captainVersion":4,
        "services":{"${'$'}${'$'}cap_appname-db":{"image":"mariadb:11","volumes":["${'$'}${'$'}cap_appname-db-data:/var/lib/mysql"],
          "environment":{"MYSQL_ROOT_PASSWORD":"${'$'}${'$'}cap_db_pass"},"caproverExtra":{"notExposeAsWebApp":"true"}},
          "${'$'}${'$'}cap_appname":{"image":"wordpress:${'$'}${'$'}cap_wp_version","depends_on":["${'$'}${'$'}cap_appname-db"]}},
        "caproverOneClickApp":{"displayName":"WordPress","description":"The most popular blogging and website platform",
          "instructions":{"start":"WordPress needs a **database**. This template creates MariaDB alongside it.","end":"Visit https://${'$'}${'$'}cap_appname.${'$'}${'$'}cap_root_domain to finish setup."},
          "variables":[{"id":"${'$'}${'$'}cap_wp_version","label":"WordPress version","defaultValue":"6.8","description":"Docker Hub tag","validRegex":"/^([^\\s^\\/])+${'$'}/"},
            {"id":"${'$'}${'$'}cap_db_pass","label":"Database password","defaultValue":"${'$'}${'$'}cap_gen_random_hex(16)"}]}}}"""
}
