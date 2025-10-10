//> using scala 3.7.2
//> using dep com.microsoft.playwright:playwright:1.55.0
//> using dep org.typelevel::cats-effect::3.6.3
//> using dep org.typelevel::log4cats-slf4j::2.7.1
//> using dep ch.qos.logback:logback-classic:1.5.18
//> using dep com.github.scopt::scopt::4.1.0
//> using dep com.github.plokhotnyuk.jsoniter-scala::jsoniter-scala-core::2.37.6
//> using dep com.github.plokhotnyuk.jsoniter-scala::jsoniter-scala-macros::2.37.6

import cats.effect.{IO, IOApp, Resource}
import cats.syntax.all.*
import ch.qos.logback.classic.{Level, LoggerContext}
import com.github.plokhotnyuk.jsoniter_scala.core.*
import com.github.plokhotnyuk.jsoniter_scala.macros.*
import com.microsoft.playwright.*
import com.microsoft.playwright.options.WaitUntilState
import org.slf4j.LoggerFactory
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jLogger
import scopt.OParser

import java.io.PrintWriter
import scala.util.Try

opaque type ModelName = String
object ModelName {
  def apply(name: String): ModelName = name
  extension (modelName: ModelName) {
    def value: String = modelName
  }
}

opaque type URL = String
object URL {
  def apply(url: String): URL = url
  extension (url: URL) {
    def value: String = url
  }
}

case class EndpointInfo(
    name: String,
    apiPath: String,
    isActive: Boolean
)

case class Config(
    debug: Boolean = false,
    models: Option[List[ModelName]] = None,
    output: String = "models.json"
)

case class ModelInfo(
    name: ModelName,
    activeEndpoints: List[EndpointInfo],
    snapshots: List[String],
    url: URL
)

// JSON codecs for jsoniter-scala
given JsonValueCodec[ModelName] = JsonCodecMaker.make[String].asInstanceOf[JsonValueCodec[ModelName]]
given JsonValueCodec[URL] = JsonCodecMaker.make[String].asInstanceOf[JsonValueCodec[URL]]
given JsonValueCodec[EndpointInfo] = JsonCodecMaker.make
given JsonValueCodec[ModelInfo] = JsonCodecMaker.make

object ModelEndpointScraper extends IOApp {

  given logger: Logger[IO] = Slf4jLogger.getLogger[IO]

  private def configureLogging(level: Level = Level.INFO): IO[Unit] = IO {
    val loggerContext = LoggerFactory.getILoggerFactory.asInstanceOf[LoggerContext]

    val rootLogger = loggerContext.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME)
    rootLogger.setLevel(Level.INFO)

    val scraperLogger = loggerContext.getLogger("<empty>.ModelEndpointScraper")
    scraperLogger.setLevel(level)
  }

  private def parseArgs(args: List[String]): IO[Either[String, Config]] = IO {
    val builder = OParser.builder[Config]
    val parser = {
      import builder.*
      OParser.sequence(
        programName("model-scraper"),
        opt[Unit]("debug")
          .action((_, c) => c.copy(debug = true))
          .text("Enable debug logging for detailed output"),
        opt[String]("models")
          .action((x, c) => c.copy(models = Some(x.split(",").map(_.trim).map(ModelName.apply).toList)))
          .text("Comma-separated list of model names to scrape (e.g., \"GPT-4o,GPT-3.5\")")
          .valueName("<model1,model2,...>"),
        opt[String]("output")
          .action((x, c) => c.copy(output = x))
          .text("Output file path for JSON endpoint-to-models mapping (default: models.json)")
          .valueName("<file.json>")
      )
    }

    OParser.parse(parser, args, Config()) match {
      case Some(config) => Right(config)
      case None =>
        if (args.contains("--help") || args.contains("-h")) {
          Left("help")
        } else {
          Left("error")
        }
    }
  }

  def run(args: List[String]): IO[cats.effect.ExitCode] =
    parseArgs(args).flatMap {
      case Right(config) =>
        runScraper(config).as(cats.effect.ExitCode.Success)
      case Left(_) =>
        IO.pure(cats.effect.ExitCode.Error)
    }

  private def runScraper(config: Config): IO[Unit] =
    for {
      _ <- configureLogging(if (config.debug) Level.DEBUG else Level.INFO)
      _ <- logger.info("🦊 Starting Firefox-based OpenAI endpoint scraper...")
      _ <- config.models.fold(IO.unit)(models => logger.info(s"🎯 Filtering for models: ${models.mkString(", ")}"))

      modelList <-
        firefoxResource.use { case (_, browser) =>
          fetchModelSet(browser, config.models)
        }
      _ <- logger.info(s"📋 Found ${modelList.size} models to scrape")
      models <- scrapeModels(modelList.toList)
      _ <- displayResults(models)
      _ <- generateAndSaveEndpointMapping(models, config.output)
    } yield ()

  case class ModelWithSnapshots(name: String, snapshots: List[String])

  given JsonValueCodec[ModelWithSnapshots] = JsonCodecMaker.make
  given JsonValueCodec[scala.collection.immutable.ListMap[String, List[ModelWithSnapshots]]] = JsonCodecMaker.make

  private def generateAndSaveEndpointMapping(models: List[ModelInfo], outputPath: String): IO[Unit] =
    for {
      _ <- logger.info(s"📝 Generating endpoint-to-models mapping...")

      endpointMapping = models
        .flatMap { model =>
          model.activeEndpoints.map { endpoint =>
            endpoint.apiPath -> ModelWithSnapshots(model.name.value, model.snapshots.sorted)
          }
        }
        .groupBy(_._1)
        .map { case (endpoint, pairs) =>
          endpoint -> pairs.map(_._2).distinctBy(_.name).sortBy(_.name)
        }
        .toSeq
        .sortBy(_._1)
        // Using ListMap to preserve order of endpoints
        .to(scala.collection.immutable.ListMap)

      _ <- logger.info(s"🔍 Found mappings for ${endpointMapping.size} endpoints")
      _ <- endpointMapping.toList.traverse_ { case (endpoint, modelInfos) =>
        val modelNames = modelInfos.map(_.name).mkString(", ")
        logger.debug(s"  $endpoint: $modelNames")
      }

      json = writeToString(endpointMapping, WriterConfig.withIndentionStep(2))
      _ <- IO.blocking {
        val writer = new PrintWriter(outputPath)
        try
          writer.write(json)
        finally
          writer.close()
      }

      _ <- logger.info(s"💾 Saved endpoint mapping to: $outputPath")
    } yield ()

  private def firefoxResource: Resource[IO, (Playwright, Browser)] =
    Resource.make(
      for {
        _ <- logger.debug("🦊 Initializing Firefox browser...")
        playwright <- IO(Playwright.create())
        browser <- IO(
          playwright
            .firefox()
            .launch(
              new BrowserType.LaunchOptions()
                .setHeadless(true)
                .setTimeout(60000)
            )
        )
      } yield (playwright, browser)
    ) { case (playwright, browser) =>
      for {
        _ <- logger.debug("🦊 Closing Firefox...")
        _ <- IO(browser.close()).handleError(_ => ())
        _ <- IO(playwright.close()).handleError(_ => ())
      } yield ()
    }

  private def navigateToPage(page: Page, url: String, timeout: Double = 60000): IO[Unit] =
    for {
      _ <- IO.blocking(
        page.navigate(
          url,
          new Page.NavigateOptions()
            .setTimeout(timeout)
            .setWaitUntil(WaitUntilState.LOAD)
        )
      )
      _ <- IO.blocking(page.waitForTimeout(3000))

      title <- IO(Option(page.title()).getOrElse("No title"))
      _ <- logger.debug(s"📄 Page title: $title")

      _ <-
        if (title.contains("Just a moment")) {
          logger.error("⚠️ Cloudflare challenge detected") *>
            IO.raiseError(
              new Exception(
                "Unfortunately it won't be possible to fetch data using this configuration because Cloudflare challenge was detected. " +
                  "This script is generally using Playwright to get around Cloudflare challenge to scrape model data. " +
                  "You can try to use different browser, but Chromium was tested as not working."
              )
            )
        } else IO.unit
    } yield ()

  private def withPage[A](browser: Browser)(operation: Page => IO[A]): IO[A] =
    for {
      page <- IO.blocking(browser.newPage())
      result <- operation(page).guarantee(IO(page.close()))
    } yield result

  private def fetchModelSet(browser: Browser, modelFilter: Option[List[ModelName]] = None): IO[Set[(ModelName, URL)]] =
    for {
      _ <- logger.info("🔍 Fetching model list from OpenAI models page...")
      result <- withPage(browser) { page =>
        (for {
          _ <- navigateToPage(page, "https://platform.openai.com/docs/models", 90000)
          _ <- IO.blocking(page.waitForTimeout(2000)) // Additional wait for models page

          modelLinks <- IO {
            import scala.jdk.CollectionConverters.*
            page.querySelectorAll("a[href^='/docs/models/']").asScala.toSet
          }
          _ <- logger.debug(s"📦 Found ${modelLinks.size} model links")

          models <- IO {
            modelLinks.flatMap { link =>
              Try {
                val href = link.getAttribute("href")
                val nameElement = link.querySelector(".font-semibold")

                if (nameElement != null && href != null) {
                  val modelName = nameElement.textContent().trim
                  val fullUrl = s"https://platform.openai.com$href"

                  if (href != "/docs/models" && modelName.nonEmpty) {
                    Some((ModelName(modelName), URL(fullUrl)))
                  } else None
                } else None
              }.toOption.flatten
            }
          }

          _ <- models.toList.traverse { case (name, url) =>
            logger.debug(s"✅ Found model: $name → $url")
          }

          filteredModels <- IO {
            modelFilter match {
              case Some(filterList) =>
                val filterSet = filterList.map(_.value.toLowerCase).toSet
                models.filter { case (modelName, _) =>
                  val nameStr = modelName.value.toLowerCase
                  filterSet.exists(filter => nameStr.contains(filter) || filter.contains(nameStr))
                }
              case None => models
            }
          }

          _ <-
            if (modelFilter.isDefined) {
              logger.info(s"🎯 Filtered to ${filteredModels.size} models matching criteria")
            } else IO.unit

        } yield filteredModels).handleErrorWith { e =>
          logger.error(s"❌ Failed to fetch model list: ${e.getMessage}") *>
            IO.raiseError(new Exception(s"Failed to fetch model list: ${e.getMessage}"))
        }
      }
    } yield result

  private def scrapeModels(modelList: List[(ModelName, URL)]): IO[List[ModelInfo]] =
    IO.parTraverseN(10)(modelList) { case (modelName, url) =>
      firefoxResource.use { case (playwright, browser) =>
        scrapeModelPage(browser, modelName, url).map(_.getOrElse(ModelInfo(modelName, Nil, Nil, url)))
      }
    }

  private def scrapeModelPage(browser: Browser, modelName: ModelName, url: URL): IO[Option[ModelInfo]] =
    for {
      _ <- logger.info(s"🔍 Scraping ${modelName.value}...")
      _ <- logger.debug(s"URL: ${url.value}")

      result <- withPage(browser) { page =>
        (for {
          _ <- navigateToPage(page, url.value)

          // Check if we still have a Cloudflare challenge after navigation
          title <- IO(Option(page.title()).getOrElse("No title"))
          result <-
            if (title.contains("Just a moment")) {
              logger.warn(s"⚠️ Cloudflare challenge detected for ${modelName.value} - skipping this model") *>
                IO.pure(None) // Skip this model but continue with others
            } else {
              for {
                textContent <- IO.blocking(page.textContent("body"))
                _ <- logger.debug(s"📊 Content length: ${textContent.length} characters")

                endpoints <- extractEndpoints(page)
                activeEndpoints = endpoints.filter(_.isActive)

                snapshots <- extractSnapshots(page, modelName)

                modelInfo = ModelInfo(
                  name = modelName,
                  activeEndpoints = activeEndpoints,
                  snapshots = snapshots,
                  url = url
                )

                _ <- logger.info(s"✅ ${modelName.value}: ${activeEndpoints.length} active endpoints, ${snapshots.length} snapshots")
              } yield Some(modelInfo)
            }
        } yield result).handleErrorWith { e =>
          logger.error(s"❌ Failed to scrape ${modelName.value}: ${e.getMessage}") *>
            IO.pure(None)
        }
      }
    } yield result

  private def extractEndpoints(page: Page): IO[List[EndpointInfo]] =
    for {
      _ <- logger.debug("🔍 Looking for endpoint cards in DOM structure...")

      endpointCards <- IO.blocking {
        import scala.jdk.CollectionConverters.*
        page.waitForTimeout(3000)

        val allCards = page.querySelectorAll("div.flex.flex-row.gap-2").asScala.toList
        allCards.filter { card =>
          Try(card.textContent()).getOrElse("").contains("v1/")
        }
      }

      _ <- logger.debug(s"📦 Found ${endpointCards.length} cards containing 'v1/' endpoints")

      endpoints <-
        if (endpointCards.nonEmpty) {
          IO.blocking {
            endpointCards.flatMap { card =>
              Try {
                val iconContainer = card.querySelector("div.rounded-lg")

                val isActive = if (iconContainer != null) {
                  val classes = iconContainer.getAttribute("class")
                  classes != null &&
                  classes.contains("bg-primary-soft") &&
                  !classes.contains("text-gray-400")
                } else false

                val nameElement = card.querySelector(".font-semibold")
                val pathElement = card.querySelector(".text-xs")
                val name = if (nameElement != null) nameElement.textContent().trim else ""
                val apiPath = if (pathElement != null) pathElement.textContent().trim else ""

                val allCardText = card.textContent().trim
                val cardLines = allCardText.split("\n").map(_.trim).filter(_.nonEmpty)

                val v1Pattern = """(v1/[a-z/]+)""".r
                val foundV1 = v1Pattern.findFirstIn(allCardText)

                val finalApiPath = if (apiPath.startsWith("v1/")) apiPath else foundV1.getOrElse("")
                val finalName =
                  if (name.nonEmpty && !name.startsWith("v1/")) name
                  else cardLines.find(line => !line.startsWith("v1/") && line.length < 30 && !line.startsWith("v1/")).getOrElse("")

                if (finalName.nonEmpty && finalApiPath.nonEmpty) {
                  Some(EndpointInfo(finalName, finalApiPath, isActive))
                } else None
              }.toOption.flatten
            }
          }.flatTap { endpoints =>
            logger.debug(s"✅ Successfully extracted ${endpoints.length} endpoints")
          }
        } else {
          logger.warn("⚠️ No endpoint cards found with expected structure") *>
            IO.pure(List.empty[EndpointInfo])
        }
    } yield endpoints

  private def extractSnapshots(page: Page, modelName: ModelName): IO[List[String]] =
    for {
      _ <- logger.debug(s"🔍 Looking for model snapshots for ${modelName.value}...")

      snapshots <- IO.blocking {
        import scala.jdk.CollectionConverters.*

        val snapshotSections = page.querySelectorAll("div").asScala.toList.filter { div =>
          val text = div.textContent()
          text.equals("Snapshots")
        }

        val extractedSnapshots = snapshotSections.flatMap { snapshotDiv =>
          try {
            val siblingElements = page.querySelectorAll("xpath=//div[text()='Snapshots']/following-sibling::div").asScala.toList

            logger.debug(s"Found ${siblingElements.size} sibling elements after Snapshots div")

            siblingElements.flatMap { contentDiv =>
              val snapshotElements = contentDiv.querySelectorAll(".text-sm").asScala.toList

              logger.debug(s"Found ${snapshotElements.size} snapshot elements in content div")

              val snapshots = snapshotElements.map { element =>
                val snapshotId = element.textContent().trim()
                logger.debug(s"Extracted snapshot ID: $snapshotId")
                snapshotId
              }
              snapshots
            }
          } catch {
            case ex: Exception =>
              throw new Exception(s"Error extracting snapshots: ${ex.getMessage}")
          }
        }
        extractedSnapshots.distinct
      }

      _ <- logger.debug(s"📸 Found ${snapshots.length} snapshots: ${snapshots.mkString(", ")}")
    } yield snapshots

  private def displayResults(models: List[ModelInfo]): IO[Unit] =
    for {
      _ <- logger.info("=" * 80)
      _ <- logger.info("🤖 OPENAI MODEL ENDPOINTS")
      _ <- logger.info("=" * 80)

      _ <- models.traverse_ { model =>
        for {
          _ <- logger.info(s"🔸 ${model.name.value}")

          _ <-
            if (model.activeEndpoints.nonEmpty) {
              logger.info("  ✅ Active Endpoints:") *>
                model.activeEndpoints.traverse_(endpoint => logger.info(s"    🟢 ${endpoint.name} → ${endpoint.apiPath}"))
            } else IO.unit

          _ <-
            if (model.snapshots.nonEmpty) {
              logger.info("  📸 Model Snapshots:") *>
                model.snapshots.traverse_(snapshot => logger.info(s"    📷 $snapshot"))
            } else IO.unit

          _ <- logger.debug(s"  🔗 Source: ${model.url.value}")
        } yield ()
      }

      totalActiveEndpoints = models.flatMap(_.activeEndpoints).length
      uniqueActiveEndpoints = models.flatMap(_.activeEndpoints.map(_.apiPath)).distinct.length
      totalSnapshots = models.flatMap(_.snapshots).length

      _ <- logger.info("=" * 80)
      _ <- logger.info(s"📊 Successfully scraped ${models.length} models")
      _ <- logger.info(s"🟢 Found $totalActiveEndpoints active endpoints ($uniqueActiveEndpoints unique)")
      _ <- logger.info(s"📸 Found $totalSnapshots model snapshots")
      _ <- logger.info("=" * 80)
    } yield ()
}
