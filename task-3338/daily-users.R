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
  q <- paste("SELECT date, r, bwp, brp, bwn, brn, bwr, brr "
      "FROM user_stats WHERE date >= '", start, "' AND date <= '", end,
      "' AND date < (SELECT MAX(date) FROM user_stats) - 1 ",
      "AND country = 'zy' ORDER BY date", sep = "")
  rs <- dbSendQuery(con, q)
  u <- fetch(rs, n = -1)
  dbDisconnect(con)
  dbUnloadDriver(drv)
  u <- data.frame(date = u$date,
       requests = u$r,
       fraction = (u$bwr * u$brn / u$bwn - u$brr) /
                (u$bwp * u$brn / u$bwn - u$brp),
       users = u$r * (u$bwp * u$brn / u$bwn - u$brp) /
               (u$bwr * u$brn / u$bwn - u$brr) / 10)
  highest <- u[u$date %in% as.Date(c("2011-04-10", "2011-04-17",
                                     "2011-04-24", "2011-05-29")), ]
  highest <- melt(highest, id.vars = 1)
  dates <- seq(from = as.Date(start, "%Y-%m-%d"),
      to = as.Date(end, "%Y-%m-%d"), by="1 day")
  missing <- setdiff(dates, u$date)
  if (length(missing) > 0)
    u <- rbind(u,
        data.frame(date = as.Date(missing, origin = "1970-01-01"),
        requests = NA, fraction = NA, users = NA))
  u <- melt(u, id.vars = 1)
  date_breaks <- date_breaks(
    as.numeric(max(as.Date(u$date, "%Y-%m-%d")) -
    min(as.Date(u$date, "%Y-%m-%d"))))
  ggplot(u, aes(x = as.Date(date, "%Y-%m-%d"), y = value)) +
    geom_line() +
    facet_grid(variable ~ ., scales = "free_y") +
    scale_x_date(name = paste("\nThe Tor Project - ",
        "https://metrics.torproject.org/", sep = "")) +
    scale_y_continuous(name = "") +
    geom_point(data = highest, size = 3, colour = alpha("red", 0.5)) +
    opts(title = paste("Estimating directly connecting users from all",
                       "countries\n(users = requests / fraction / 10)\n"))
  ggsave(filename = path, width = 8, height = 5, dpi = as.numeric(dpi))
}
plot_dirbytes("2011-01-01", "2011-06-23", "daily-users.pdf", 300)

