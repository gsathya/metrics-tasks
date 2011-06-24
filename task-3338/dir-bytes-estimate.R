library("RPostgreSQL")
library("DBI")
library("ggplot2")
library("proto")
library("grid")
library("reshape")
library("plyr")
library("digest")

db = "tordir"
dbuser = "metrics"
dbpassword= "password" ###### <- put in real password here!

plot_dirbytes <- function(start, end, path, dpi) {
  drv <- dbDriver("PostgreSQL")
  con <- dbConnect(drv, user = dbuser, password = dbpassword, dbname = db)
  q <- paste("SELECT date, r, bwp, brp, bwn, brn, bwr, brr, dw, bwd ",
      "FROM user_stats WHERE date >= '", start, "' AND date <= '", end,
      "' AND date < (SELECT MAX(date) FROM user_stats) - 1 ",
      "AND country = 'zy' ORDER BY date", sep = "")
  rs <- dbSendQuery(con, q)
  u <- fetch(rs, n = -1)
  dbDisconnect(con)
  dbUnloadDriver(drv)
  u <- data.frame(date = u$date,
       estimated = floor((u$bwp * u$brn / u$bwn - u$brp) / 86400),
       estimatedreq = floor((u$bwr * u$brn / u$bwn - u$brr) / 86400),
       extrapolated = floor(u$dw * u$bwp / u$bwd / 86400))
  dates <- seq(from = as.Date(start, "%Y-%m-%d"),
      to = as.Date(end, "%Y-%m-%d"), by="1 day")
  missing <- setdiff(dates, u$date)
  if (length(missing) > 0)
    u <- rbind(u,
        data.frame(date = as.Date(missing, origin = "1970-01-01"),
        estimated = NA, estimatedreq = NA, extrapolated = NA))
  u <- melt(u, id.vars = 1)
  highest <- u[u$date %in% as.Date(c("2011-04-10", "2011-04-17",
    "2011-04-24", "2011-05-29")) & u$variable == "estimatedreq", ]
  date_breaks <- date_breaks(
    as.numeric(max(as.Date(u$date, "%Y-%m-%d")) -
    min(as.Date(u$date, "%Y-%m-%d"))))
  ggplot(u, aes(x = as.Date(date, "%Y-%m-%d"), y = value / 2^20,
    colour = variable)) +
    geom_line() +
    scale_x_date(name = paste("\nThe Tor Project - ",
        "https://metrics.torproject.org/", sep = "")) +
    scale_y_continuous(name = "",
      limits = c(0, max(u$value, na.rm = TRUE) / 2^20)) +
    scale_colour_hue(name = "",
      breaks = c("extrapolated", "estimated", "estimatedreq"),
      labels = c(paste("extrapolated from reported directory bytes (all",
               "directory mirrors)"),
               "estimated from total bytes (all directory mirrors)",
               paste("estimated from total bytes (only directory mirrors",
               "reporting stats)"))) +
    geom_point(data = highest, size = 3, colour = alpha("purple", 0.35)) +
    opts(title = paste("Estimated vs. extrapolated written directory",
                       "bytes in MiB/s per day"),
      legend.position = "top")
  ggsave(filename = path, width = 8, height = 5, dpi = as.numeric(dpi))
}
plot_dirbytes("2009-08-19", "2011-06-23", "dir-bytes-estimate.pdf", 300)

