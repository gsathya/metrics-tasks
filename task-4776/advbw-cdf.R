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
dbpassword= "password"

plot_advbw_cdf <- function(datetime, path, xlimit, dpi) {
  drv <- dbDriver("PostgreSQL")
  con <- dbConnect(drv, user = dbuser, password = dbpassword, dbname = db)
  q <- paste("SELECT LEAST(descriptor.bandwidthavg, ",
             "descriptor.bandwidthburst, ",
             "descriptor.bandwidthobserved) AS advbw ",
             "FROM statusentry ",
             "RIGHT JOIN descriptor ",
             "ON statusentry.descriptor = descriptor.descriptor ",
             "WHERE statusentry.validafter = '", datetime, "' ",
             "ORDER BY 1", sep = "")
  rs <- dbSendQuery(con, q)
  u <- fetch(rs, n = -1)
  dbDisconnect(con)
  dbUnloadDriver(drv)
  ggplot(u, aes(x = sort(advbw / 1024),
    y = (1:length(advbw)) / length(advbw))) +
  geom_line(size = 1) +
  scale_x_continuous(name = "\nAdvertised bandwidth in KiB/s",
    limit = c(0, xlimit)) +
  scale_y_continuous(formatter = "percent",
    name = "Fraction of relays with that bandwidth or less\n") +
  opts(title = paste(datetime, "\n", sep = ""))
  ggsave(filename = path, width = 8, height = 5, dpi = as.numeric(dpi))
}
plot_advbw_cdf('2011-02-03 12:00:00', 'advbw-cdf-2011-02-03-12-00-00.png',
  1024, 72)
plot_advbw_cdf('2011-03-03 12:00:00', 'advbw-cdf-2011-03-03-12-00-00.png',
  1024, 72)
plot_advbw_cdf('2011-04-03 12:00:00', 'advbw-cdf-2011-04-03-12-00-00.png',
  1024, 72)
plot_advbw_cdf('2011-05-03 12:00:00', 'advbw-cdf-2011-05-03-12-00-00.png',
  1024, 72)
plot_advbw_cdf('2011-06-03 12:00:00', 'advbw-cdf-2011-06-03-12-00-00.png',
  1024, 72)
plot_advbw_cdf('2011-07-03 12:00:00', 'advbw-cdf-2011-07-03-12-00-00.png',
  1024, 72)
plot_advbw_cdf('2011-08-03 12:00:00', 'advbw-cdf-2011-08-03-12-00-00.png',
  1024, 72)
plot_advbw_cdf('2011-09-03 12:00:00', 'advbw-cdf-2011-09-03-12-00-00.png',
  1024, 72)
plot_advbw_cdf('2011-10-03 12:00:00', 'advbw-cdf-2011-10-03-12-00-00.png',
  1024, 72)
plot_advbw_cdf('2011-11-03 12:00:00', 'advbw-cdf-2011-11-03-12-00-00.png',
  1024, 72)
plot_advbw_cdf('2011-12-03 12:00:00', 'advbw-cdf-2011-12-03-12-00-00.png',
  1024, 72)
plot_advbw_cdf('2012-01-03 12:00:00', 'advbw-cdf-2012-01-03-12-00-00.png',
  1024, 72)

