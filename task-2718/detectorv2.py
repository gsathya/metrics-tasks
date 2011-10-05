##  Copyright (c) 2011 George Danezis <gdane@microsoft.com>
##
##  All rights reserved.
##
##  Redistribution and use in source and binary forms, with or without
##  modification, are permitted (subject to the limitations in the
##  disclaimer below) provided that the following conditions are met:
##
##   * Redistributions of source code must retain the above copyright
##     notice, this list of conditions and the following disclaimer.
##
##   * Redistributions in binary form must reproduce the above copyright
##     notice, this list of conditions and the following disclaimer in the
##     documentation and/or other materials provided with the
##     distribution.
##
##   * Neither the name of <Owner Organization> nor the names of its
##     contributors may be used to endorse or promote products derived
##     from this software without specific prior written permission.
##
##  NO EXPRESS OR IMPLIED LICENSES TO ANY PARTY'S PATENT RIGHTS ARE
##  GRANTED BY THIS LICENSE.  THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT
##  HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED
##  WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
##  MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
##  DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE
##  LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
##  CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
##  SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR
##  BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
##  WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
##  OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN
##  IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
##
##  (Clear BSD license: http://labs.metacarta.com/license-explanation.html#license)

##  This script reads a .csv file of the number of Tor users and finds
##  anomalies that might be indicative of censorship.

# Dep: matplotlib
from pylab import * 
import matplotlib

# Dep: numpy
import numpy 

# Dep: scipy
import scipy.stats 
from scipy.stats.distributions import norm
from scipy.stats.distributions import poisson
from scipy.stats.distributions import gamma

# Std lib
from datetime import date
from datetime import timedelta
import os.path

import random

days = ["Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"]

# read the .csv file
class torstatstore:
  def __init__(self, file_name, DAYS):
    self.DAYS = DAYS
    f = file(file_name)
    country_codes = f.readline()
    country_codes = country_codes.strip().split(",")

    store = {}
    MAX_INDEX = 0
    for i, line in enumerate(f):
        MAX_INDEX += 1
        line_parsed = line.strip().split(",")
        for j, (ccode, val) in enumerate(zip(country_codes,line_parsed)):
            processed_val = None
            if ccode == "date":
                try:
                    year, month, day = int(val[:4]), int(val[5:7]), int(val[8:10])                
                    processed_val = date(year, month, day)
                except Exception, e:
                    print "Parsing error (ignoring line %s):" % j
                    print "%s" % val,e
                    break            
            
            elif val != "NA":
                processed_val = int(val)
            store[(ccode, i)] = processed_val

    # min and max
    date_min = store[("date", 0)]
    date_max = store[("date", i)]

    all_dates = []
    d = date_min
    dt = timedelta(days=1)
    while d <= date_max:
        all_dates += [d]    
        d = d + dt

    # Save for later
    self.store = store
    self.all_dates = all_dates
    self.country_codes = country_codes
    self.MAX_INDEX = MAX_INDEX
    self.date_min = date_min
    self.date_max = date_max

  def get_country_series(self, ccode):
    assert ccode in self.country_codes
    series = {}
    for d in self.all_dates:
        series[d] = None
    for i in range(self.MAX_INDEX):
        series[self.store[("date", i)]] = self.store[(ccode, i)]
    sx = []
    for d in self.all_dates:
        sx += [series[d]]
    return sx[-self.DAYS:]

  def get_dates(self):
    return self.all_dates[-self.DAYS:]

  def get_largest(self, number):
    exclude = set(["all", "??", "date"])
    l = [(self.store[(c, self.MAX_INDEX-1)], c) for c in self.country_codes if c not in exclude]
    l.sort()
    l.reverse()
    return [c for _, c in l][:number]

  def get_largest_locations(self, number):
    l = self.get_largest(number)
    res = {}
    for ccode in l[:number]:
      res[ccode] = self.get_country_series(ccode)
    return res

  def get_codes(self):
    return self.country_codes + []

## Run a particle filter based inference algorithm
## given a data series and a model of traffic over time
def particle_filter_detector(ser1, taps, models):
  # particle : (id, rate, censor, last_censor prev_particle)
  
  # Model paramaters
  normal_std_factor = 4
  censorship_std_factor = 7
  censorship_prior_model = 0.01
  change_tap_prior_model = 0.1

  # Sampling parameters
  change_tap_sample = 0.2
  censorship_prior_sample = 0.3
  particle_number = 1000
  mult_particles = 1
  
  # Check consistancy once
  for t in models:    
    assert len(ser1) == len(models[t]) 

  # Clean up a bit the data
  series2 = []
  last = None
  first = None
  # Process series
  for s in ser1:
    if s == None:
      series2 += [last]
    else:
      if first == None:
        first = s
      series2 += [s]
      last = s

  series2 = [s if s != None else first for s in series2]
  series = series2

  # Data structures to keep logs
  particles = {}
  outputlog = [(series[0],series[0])]

  # Initial particles:
  particles[0] = []
  G = gamma(max(1,series[0]), 1)
  for pi, r in enumerate(G.rvs(particle_number)):
    particles[0] += [(pi, r, False, None, 0, random.choice(taps), False)]

  # Now run the sampler for all times
  for pi in range(1, len(series)):
    assert models != None
    assert taps != None

    # Normal distributions from taps and the model standard deviation for normality and censorship
    round_models = {}
    for ti in taps:
      NoCensor = norm(models[ti][pi][0], (models[ti][pi][1] * normal_std_factor)**2)
      Censor = norm(models[ti][pi][0], (models[ti][pi][1] * censorship_std_factor)**2)
      round_models[ti] = (NoCensor, Censor)

    # Store for expanded pool of particles
    temporary_particles = []

    # Expand the distribution
    for p in particles[pi-1]:
      p_old, C_old, j = tracebackp(particles, p, pi-1, p[5] - 1) # taps[0] - 1)

      # Serial number of old particle
      p_old_num = None
      if p_old != None:
        p_old_num = p_old[0]

      # Create a number of candidate particles from each previous particle
      for _ in range(mult_particles):

        # Sample a new tap for the candidate particle
        new_tap = p[5]
        if random.random() < change_tap_sample:
          new_tap = random.choice(taps)
        
        # Update this censorship flag
        C = False  
        if random.random() < censorship_prior_sample:
          C = True

        # Determine new rate
        new_p = None
        if p_old == None:          
          new_p = p[1] # continue as before
        if C | C_old:
          while new_p == None or new_p < 0:
            new_p = p_old[1] * (1 + round_models[new_tap][1].rvs(1)[0]) ## censor models
        else:
          while new_p == None or new_p < 0:
            new_p = p_old[1] * (1 + round_models[new_tap][0].rvs(1)[0]) ## no censor models
        
        # Build and register new particle
        newpi = (None, new_p, C, p[0], pi, new_tap, C | C_old)
        temporary_particles += [newpi]


    # Assign a weight to each sampled candidtae particle
    weights = []
    for px in temporary_particles:
      wx = 1.0

      # Adjust weight to observation
      if not series[pi] == None:
        poisson_prob = poisson.pmf(series[pi], px[1])
        #print poisson_prob, px
        wx *= poisson_prob

      # Adjust the probability of censorship
      if px[2]:
        wx *= censorship_prior_model / censorship_prior_sample
      else:
        wx *= (1 - censorship_prior_model) / (1 - censorship_prior_sample)

      # Adjust the probability of changing the tap
      if px[5] == particles[pi-1][px[3]][5]:
        wx *= (1 - change_tap_prior_model) / (((1-change_tap_sample) + change_tap_sample*(1.0 / len(taps))))
      else:
        wx *= (change_tap_prior_model) / (1 - (((1-change_tap_sample) + change_tap_sample*(1.0 / len(taps)))))
          
      weights += [wx]

    weights_sum = sum(weights)
    
    ## Resample according to weight
    particles[pi] = []
    for pid in range(particle_number):
      px = samplep(weights, weights_sum, temporary_particles)
      px = (pid, px[1], px[2], px[3], px[4], px[5], px[6])
      particles[pi] += [px]

    ## Collect some statistics

    ## stats
    Ci = 0
    mean = 0
    for px in particles[pi]:
      if px[2]:
        Ci += 1
      mean += px[1]
    mean = mean / len(particles[pi])

    # Diversity
    Div = len(set([pv[3] for pv in particles[pi]]))

    # Range of values
    range_normal = sorted([pn[1] for pn in temporary_particles if not pn[2]])    
    Base = range_normal[len(range_normal)/2]
    Mn = range_normal[len(range_normal)*1/100]
    Mx = range_normal[len(range_normal)*99/100]
    outputlog += [(Mn, Mx)]

    # How many are using the censorship model at any time?
    censor_model_stat = len([1 for pn in particles[pi] if pn[6]])* 100 / len(particles[pi])

    # Build histogram of taps
    tap_hist = {}
    for px in particles[pi]:
      tap_hist[px[5]] = tap_hist.get(px[5], 0) + 1
          
    print "%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s" % (pi, Ci, mean, series[pi], tap_hist, Base, Mn, Mx, Div, censor_model_stat)
    # print "      [%s - %s]" % (key_series_point*(1+NoCensor.ppf(0.00001)), key_series_point*(1+NoCensor.ppf(0.99999)))

  return particles, outputlog

## Get number of censorship particles, particles that use previous censorship models,
## and total number of particles over time.
def get_events(particles):
  events = []
  for ps in sorted(particles):
    censor_model_stat = len([1 for pn in particles[ps] if pn[6]])
    events += [(len([1 for p in particles[ps] if p[2]]), censor_model_stat, len(particles[ps]))]
  return events

## Make pretty graphs of the data and censorship events
def plotparticles(series, particles, outputlog, labels, xtitle, events):
    assert len(xtitle) == 3
    fname, stitle, slegend = xtitle

    font = {'family' : 'Bitstream Vera Sans',
        'weight' : 'normal',
        'size'   : 8}
    matplotlib.rc('font', **font)

    mmx = max(series)
    if mmx == None:
      return # There is no data here!
    diff = abs(-mmx*0.1 - mmx*1.1)

    ylim( (-mmx*0.1, 1+mmx*1.1) )
    plot(labels, series, linewidth=1.0, label="Users")    

    F = gcf()

    wherefill = []
    minc, maxc = [], []
    for mm,mx in outputlog:
      wherefill += [not (mm == None and mx == None)] 
      assert mm <= mx or (mm == None and mx == None)
      minc += [mm]
      maxc += [mx]
          
    fill_between(labels, minc, maxc, where=wherefill, color="gray", label="Prediction")

    vdown = []
    vup = []
    active_region_20 = []
    active_region_50 = []
    for i,v in enumerate(series):
      if minc[i] == None or maxc[i] == None:
        continue

      mean = (minc[i] + maxc[i]) / 2

      v2 = v
      if v2 == None:
        v2 = 0

      if events[i][0] * 100 / events[i][2] > 10:      
        if v2 <= mean:
          vdown += [(labels[i], v2, events[i][0], events[i][2])]
          # print vdown[-1]
        else:
          vup += [(labels[i], v2, events[i][0], events[i][2])]

      active_region_20 += [events[i][1] * 100 / events[i][2] > 20]
      active_region_50 += [events[i][1] * 100 / events[i][2] > 50]
                
    fill_between(labels, -mmx*0.1*ones(len(labels)), (1+mmx*1.1)*ones(len(labels)), where=active_region_20, color="r", alpha=0.15)
    fill_between(labels, -mmx*0.1*ones(len(labels)), (1+mmx*1.1)*ones(len(labels)), where=active_region_50, color="r", alpha=0.15)
    
    x = [p[0] for p in vdown]
    y = [p[1] for p in vdown]
    s = [20 + p[2]*100 / p[3] for p in vdown]
    if len(x) > 0:
      scatter(x,y,s=s, marker='v', c='r')
    for xi,yi, score, total in vdown:
      if 100 * score / total > 10:
        text(xi, yi - diff*5 / 100, "%2d%%" % (100 * float(score) / total), color="r")

    x = [p[0] for p in vup]
    y = [p[1] for p in vup]
    s = [20+ p[2]*100 / p[3] for p in vup]
    if len(x) > 0:
      scatter(x,y,s=s, marker='^', c='g')
    for xi,yi, score, total in vup:
      if 100 * score / total > 10:
        text(xi, yi+diff*5 / 100, "%2d%%" % (100 * float(score) / total), color="g")


    legend(loc=2)

    xlabel('Time (days)')
    ylabel('Users')
    title(stitle)
    grid(True)
    

    F.set_size_inches(10,5)
    F.savefig(fname,  format="png", dpi = (150))
    close()

## Get a particle from a trace at time current_round - delay
def tracebackp(particles, start_particle, current_round, delay):
  if current_round - delay < 0:
    return None, False, 0

  j = current_round
  this_particle = start_particle
  C = False
  r = None
  # print "-----"
  while not j < current_round - delay:
    # print this_particle
    C |= this_particle[2] # set the censorship flag
    j = j-1
    if not (not j < current_round - delay):
      break
    this_particle = particles[j][this_particle[3]]
  assert j+1 == this_particle[4] == current_round - delay
  return (this_particle, C, j+1)

# Sample a number of items according to their weights
def samplep(weights, total, samples):
  rx = random.random() * total
  stotal = 0.0
  for i,w in enumerate(weights):
    stotal += w
    if stotal >= rx:
      return samples[i]

  assert False

# Makes an estimate of the rate from sample observations
def infer_sample_rate(series):
  seriesNone = series + []
  series = series + [] # we need a fresh copy!
  for i,s in enumerate(series):
    if seriesNone[i] == None:
      series[i] = 0
    if series[i] == 0:
      series[i] = 0.001
  rates = list(gamma.rvs(series, 1))
  for i,r in enumerate(rates):
    if seriesNone[i] == None or seriesNone[i] == 0:
      rates[i] = None
  return rates

# Get (mean, std) for the top-50 series and different day delays (e.g. taps=[1,7])
def make_daytoday_normal_models(tss, taps):
  codes = tss.get_largest_locations(50)
  series = {}
  L = len(codes.values()[0])
  for c in codes:
    series[(c, 0)] = infer_sample_rate(tss.get_country_series(c))
    assert len(series[(c, 0)]) == L
    for d in taps:
      series[(c, d)] = historic_frac(series[(c, 0)], d)
      assert len(series[(c, d)]) == L

  models = {}
  for d in taps:
    models[d] = []
    for i in range(L):
      v = []
      for c in codes:
        vi = series[(c, d)][i]
        if not vi == None:
          v += [vi]
      if len(v) > 1:
        v.sort()
        v = v[len(v)*5/100:len(v)*95/100:]
        if (numpy.mean(v) > 10) or (numpy.mean(v) < -10):
          # models[d] += [(0.0, 0.02)]
          print i, [(numpy.mean(v), numpy.std(v))]
          models[d] += [(numpy.mean(v), numpy.std(v))]        
        else:
          models[d] += [(numpy.mean(v), numpy.std(v))]        
      else:
        models[d] += [(0.0, 0.02)]
      # print d, i, models[d][-1] # , v[:5]    

  return models

# Get historic fractions of traffic to train models
def historic_frac(rates, delay):
  assert delay > 0
  diff= []
  for i,r in enumerate(rates):
    if i - delay < 0 or rates[i-delay] == None or rates[i] == None or rates[i-delay] == 0:
      diff += [None]
    else:
      diff += [(rates[i] - rates[i-delay]) / rates[i-delay]]
  return diff
  
def plot_country(tss, models, taps, country_code, GRAPH_DIR):
  series = tss.get_country_series(country_code)
  particles, outputlog = particle_filter_detector(series, taps, models) ## Run the inference algorithm
  events = get_events(particles) ## Extract events from particles -- % censorship over time
  labels = tss.get_dates()
  xtitle = (os.path.join(GRAPH_DIR, "%s-censor.png" % country_code), "Tor report for %s" % country_code,"")
  plotparticles(series, particles, outputlog, labels, xtitle, events)


#def main():
if True:
  # Change these to customize script
  ## (Model parameters are still in particle filter function)
  CSV_FILE = "direct-users.csv"
  GRAPH_DIR = "img2"
  DAYS= 4 * 31
  
  tss = torstatstore(CSV_FILE, DAYS)
  gr = tss.get_country_series('gr')
  rates_gr =  infer_sample_rate(gr)
  # print historic_frac(rates_gr, 1)
  
  models = make_daytoday_normal_models(tss, [1, 7])

  # for country_code in tss.get_largest(250):
  #country_code = "kr"
  for country_code in ["cn", "eg", "ly", "kr", "de", "mm"]:
    print country_code
    plot_country(tss, models, [1, 7], country_code, GRAPH_DIR)
      
#if __name__ == "__main__":
#    main()
